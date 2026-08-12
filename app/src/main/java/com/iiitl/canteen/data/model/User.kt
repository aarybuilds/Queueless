package com.iiitl.canteen.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val rollNumber: String = "",
    val role: UserRole = UserRole.STUDENT,
    val noShowCount: Int = 0,
    val assignedCafeteriaId: String = ""
)
