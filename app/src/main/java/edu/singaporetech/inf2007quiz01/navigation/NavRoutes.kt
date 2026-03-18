package edu.singaporetech.inf2007quiz01.navigation

/** Nav3 route keys — used as backstack entries in NavDisplay. */

/** Route for the contact screen (CalBot list). */
data object ContactRoute

/** Route for the calculator screen, carrying which CalBot was selected. */
data class CalculatorRoute(val calBotId: Int)
