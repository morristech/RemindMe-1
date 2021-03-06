package com.physphil.android.remindme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.physphil.android.remindme.reminders.ReminderActivity
import com.physphil.android.remindme.reminders.list.DeleteAllDialogFragment
import com.physphil.android.remindme.reminders.list.DeleteReminderDialogFragment
import com.physphil.android.remindme.reminders.list.ReminderListAdapter
import com.physphil.android.remindme.room.entities.Reminder
import com.physphil.android.remindme.ui.ProgressSpinner
import com.physphil.android.remindme.ui.ReminderListDivider
import com.physphil.android.remindme.util.setVisibility
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class MainActivity : BaseActivity(), ReminderListAdapter.ReminderListAdapterClickListener,
        DeleteAllDialogFragment.Listener,
        DeleteReminderDialogFragment.Listener {

    @Inject
    lateinit var viewModelFactory: MainActivityViewModelFactory

    @BindView(R.id.reminder_list_recyclerview)
    lateinit var recyclerView: RecyclerView

    @BindView(R.id.reminder_list_fab)
    lateinit var fab: FloatingActionButton

    @BindView(R.id.reminder_list_spinner)
    lateinit var spinner: ProgressSpinner

    @BindView(R.id.reminder_list_empty)
    lateinit var empty: TextView

    private val disposables = CompositeDisposable()
    private val adapter = ReminderListAdapter()
    private val viewModel: MainActivityViewModel by lazy { ViewModelProviders.of(this, viewModelFactory).get(MainActivityViewModel::class.java) }
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        (application as RemindMeApplication).applicationComponent.inject(this)
        ButterKnife.bind(this)
        setupRecyclerview()

        // Create required notification channel on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_NOTIFICATIONS, getString(R.string.channel_notifications), NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        bindViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    private fun setupRecyclerview() {
        adapter.setOnClickListener(this)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = DefaultItemAnimator()

        // Setup list divider
        val inset = resources.getDimensionPixelSize(R.dimen.reminder_divider_margin)
        val divider = ReminderListDivider(InsetDrawable(getDrawable(R.drawable.divider), inset, 0, inset, 0))
        recyclerView.addItemDecoration(divider)

        // Setup random list header
        val headers = resources.getStringArray(R.array.reminder_list_headers)
        val index = (Math.random() * headers.size).toInt()
        adapter.headerText = headers[index]
    }

    private fun bindViewModel() {
        // Observe all LiveData from ViewModel
        viewModel.clearNotificationEvent.observe(this, deleteNotificationsObserver)
        viewModel.showDeleteAllConfirmationEvent.observe(this, showDeleteAllConfirmationObserver)
        viewModel.showDeleteConfirmationEvent.observe(this, showDeleteConfirmationObserver)
        viewModel.getSpinnerVisibility().observe(this, spinnerVisibilityObserver)
        viewModel.getEmptyVisibility().observe(this, emptyVisibilityObserver)
        disposables.add(viewModel.reminderList.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    // onNext
                    adapter.setReminderList(it)
                    viewModel.reminderListUpdated(it)
                    fab.show()  // make sure the fab is always showing when the list is updated
                }, {
                    // onError
                }))
    }

    @OnClick(R.id.reminder_list_fab)
    fun onAddReminderClick() {
        startActivity(ReminderActivity.intent(this))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_delete_all -> {
                viewModel.confirmDeleteAllReminders()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // region ViewModel observers
    private val spinnerVisibilityObserver = Observer<Boolean> {
        it?.let { spinner.setVisibility(it) }
    }

    private val emptyVisibilityObserver = Observer<Boolean> {
        it?.let { empty.setVisibility(it) }
    }

    private val deleteNotificationsObserver = Observer<Int?> {
        if (it != null) {
            notificationManager.cancel(it)
        } else {
            notificationManager.cancelAll()
        }
    }

    private val showDeleteAllConfirmationObserver = Observer<Void> {
        DeleteAllDialogFragment.newInstance().show(supportFragmentManager, DeleteAllDialogFragment.TAG)
    }

    private val showDeleteConfirmationObserver = Observer<Void> {
        DeleteReminderDialogFragment.newInstance().show(supportFragmentManager, DeleteReminderDialogFragment.TAG)
    }
    // endregion

    // region ReminderListAdapterClickListener implementation
    override fun onReminderClicked(reminder: Reminder) {
        startActivity(ReminderActivity.intent(this, reminder.id))
    }

    override fun onDeleteReminder(reminder: Reminder) {
        viewModel.confirmDeleteReminder(reminder)
    }
    // endregion

    // region DeleteAllDialogFragment.Listener implementation
    override fun onDeleteAllReminders() {
        viewModel.deleteAllReminders()
    }
    // endregion

    // region DeleteReminderDialogFragment.Listener implementation
    override fun onDeleteReminder() {
        viewModel.deleteReminder()
    }

    override fun onCancel() {
        viewModel.cancelDeleteReminder()
    }
    // endregion
}
