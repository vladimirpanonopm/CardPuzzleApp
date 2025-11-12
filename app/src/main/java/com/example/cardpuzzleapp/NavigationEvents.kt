package com.example.cardpuzzleapp

/**
 * Определяет события навигации, которые может отправить CardViewModel.
 * ViewModel больше не знает о "маршрутах" NavHost.
 */
sealed class NavigationEvent {
    /**
     * Запрос на показ определенного раунда.
     * AppNavigation решит, какой экран (Game или Matching) использовать.
     */
    data class ShowRound(val levelId: Int, val roundIndex: Int) : NavigationEvent()

    /**
     * Запрос на показ экрана "результатов" (паззла) для уровня.
     */
    data class ShowRoundTrack(val levelId: Int) : NavigationEvent()
}

/**
 * Определяет события завершения, которые отправляет MatchingViewModel.
 */
sealed class MatchingCompletionEvent {
    object Win : MatchingCompletionEvent()
    object Track : MatchingCompletionEvent()
    object Skip : MatchingCompletionEvent()
}