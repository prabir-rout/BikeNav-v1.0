package com.bikenv.app

data class ORSResponse(
    val routes: List<Route>
)

data class Route(
    val segments: List<Segment>
)

data class Segment(
    val steps: List<Step>
)

data class Step(
    val distance: Double,
    val instruction: String,
    val type: Int,
    val name: String?
)