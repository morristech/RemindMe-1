package com.physphil.android.remindme

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import com.physphil.android.remindme.data.ReminderRepo
import com.physphil.android.remindme.job.JobRequestScheduler
import com.physphil.android.remindme.room.entities.Reminder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.initMocks

/**
 * Copyright (c) 2018 Phil Shadlyn
 */
class MainActivityViewModelTest {

    /*
     * This is required to test LiveData. When setting values Android checks to see what thread the call is made from,
     * and this rule returns the required result to avoid an NPE
     */
    @get: Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var repo: ReminderRepo

    @Mock
    private lateinit var scheduler: JobRequestScheduler

    @Mock
    private lateinit var observer: Observer<Any>

    private lateinit var viewModel: MainActivityViewModel

    @Before
    fun setUp() {
        initMocks(this)
        viewModel = MainActivityViewModel(repo, scheduler)
    }

    @Test
    fun testEmptyReminderListUpdated() {
        val reminderList = MutableLiveData<List<Reminder>>()
        reminderList.value = listOf()
        `when`(repo.getActiveReminders()).thenReturn(reminderList)

        viewModel = MainActivityViewModel(repo, scheduler)
        viewModel.reminderListUpdated()
        assert(viewModel.getSpinnerVisibility().value == false)
        assert(viewModel.getEmptyVisibility().value == true)
    }

    @Test
    fun testReminderListUpdated() {
        val reminderList = MutableLiveData<List<Reminder>>()
        reminderList.value = listOf(Reminder(), Reminder())
        `when`(repo.getActiveReminders()).thenReturn(reminderList)

        viewModel = MainActivityViewModel(repo, scheduler)
        viewModel.reminderListUpdated()
        assert(viewModel.getSpinnerVisibility().value == false)
        assert(viewModel.getEmptyVisibility().value == false)
    }

    @Test
    fun testConfirmDeleteAllReminders() {
        viewModel.showDeleteAllConfirmationEvent.observeForever(observer as Observer<Void>)

        viewModel.confirmDeleteAllReminders()
        verify(observer).onChanged(null)
    }

    @Test
    fun testDeleteAllReminders() {
        viewModel.clearNotificationEvent.observeForever(observer as Observer<Int?>)

        viewModel.deleteAllReminders()
        verify(scheduler).cancelAllJobs()
        verify(repo).deleteAllReminders()
        verify(observer).onChanged(null)
    }

    @Test
    fun testConfirmDeleteReminder() {
        viewModel.showDeleteConfirmationEvent.observeForever(observer as Observer<Void>)

        viewModel.confirmDeleteReminder(Reminder())
        verify(observer).onChanged(null)
    }

    @Test
    fun testDeleteReminder() {
        viewModel.clearNotificationEvent.observeForever(observer as Observer<Int?>)
        val reminder = Reminder()

        viewModel.confirmDeleteReminder(reminder)
        viewModel.deleteReminder()
        verify(scheduler).cancelJob(reminder.externalId)
        verify(repo).deleteReminder(reminder)
        verify(observer).onChanged(reminder.notificationId)
    }
}