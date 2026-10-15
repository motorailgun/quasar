package es.aria_on_the_planet.quasar.domain.model

enum class TimelineType {
    Home,
    Local,
    Global,
}

data class Timeline(
    val type: TimelineType,
    val posts: List<Post>,
)
