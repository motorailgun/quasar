# テスト用のアカウント (user1〜user10)・アクセストークン・カスタム絵文字を作る (冪等)。
# testbed.sh が `rails runner` で各サーバに対して走らせ、標準出力の最後の行の JSON を state/tokens/<server>.json に書く。
# 3.5 と 4.x の両方で動くよう、モデルを直接触るのは両方に共通する所だけにしている。
require 'json'
require 'zlib'
require 'tempfile'

COUNT = 10
PASSWORD = 'quasar-testbed'
SCOPES = 'read write follow push'
domain = ENV.fetch('LOCAL_DOMAIN')

# 単色 + 縁取りの PNG (絵文字用)
def emoji_png(rgb, size = 64)
  rows = (0...size).map do |y|
    row = +"\x00".b
    size.times do |x|
      edge = x < 4 || y < 4 || x >= size - 4 || y >= size - 4
      row << (edge ? [40, 40, 40] : rgb).pack('C3')
    end
    row
  end
  chunk = ->(kind, data) { [data.bytesize].pack('N') + kind + data + [Zlib.crc32(kind + data)].pack('N') }
  "\x89PNG\r\n\x1a\n".b +
    chunk.call('IHDR', [size, size, 8, 2, 0, 0, 0].pack('NNC5')) +
    chunk.call('IDAT', Zlib::Deflate.deflate(rows.join)) +
    chunk.call('IEND', '')
end

app = Doorkeeper::Application.find_or_create_by!(name: 'quasar-testbed') do |a|
  a.redirect_uri = 'urn:ietf:wg:oauth:2.0:oob'
  a.scopes = SCOPES
end

accounts = (1..COUNT).map do |n|
  username = "user#{n}"
  account = Account.find_local(username)
  unless account
    user = User.new(email: "#{username}@#{domain}", password: PASSWORD, agreement: true, confirmed_at: Time.now.utc)
    # 4.x で bypass_invite_request_check から改名された
    %i(bypass_registration_checks= bypass_invite_request_check=).each { |m| user.public_send(m, true) if user.respond_to?(m) }
    user.account = Account.new(username: username)
    user.save!
    account = user.account
  end
  user = account.user
  user.update!(approved: true) if user.respond_to?(:approved) && !user.approved

  token = Doorkeeper::AccessToken.where(application_id: app.id, resource_owner_id: user.id, revoked_at: nil).first ||
          Doorkeeper::AccessToken.create!(application_id: app.id, resource_owner_id: user.id, scopes: SCOPES)
  { username: username, id: account.id.to_s, token: token.token, email: user.email }
end

emojis = { 'quasar' => [90, 110, 230], 'testbed' => [230, 120, 60], 'blobok' => [90, 200, 120] }.map do |shortcode, rgb|
  unless CustomEmoji.local.exists?(shortcode: shortcode)
    Tempfile.create([shortcode, '.png'], binmode: true) do |f|
      f.write(emoji_png(rgb))
      f.flush
      f.rewind
      CustomEmoji.create!(shortcode: shortcode, domain: nil, image: f, visible_in_picker: true)
    end
  end
  shortcode
end

puts({ domain: domain, password: PASSWORD, accounts: accounts, emojis: emojis }.to_json)
