package com.crossbowffs.nekosms.app;

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.crossbowffs.nekosms.R;
import com.crossbowffs.nekosms.data.BlockedSmsLoader;
import com.crossbowffs.nekosms.data.SmsMessageData;
import com.crossbowffs.nekosms.provider.NekoSmsContract;
import com.crossbowffs.nekosms.utils.Xlog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class BlockedSmsListActivity extends ListActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static class MessageListItemTag {
        public SmsMessageData mMessageData;
        public TextView mSenderTextView;
        public TextView mBodyTextView;
        public TextView mTimeSentTextView;
    }

    private static class BlockedSmsAdapter extends ResourceCursorAdapter {
        private int[] mColumns;

        public BlockedSmsAdapter(Context context) {
            super(context, R.layout.listitem_blockedsms_list, null, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = super.newView(context, cursor, parent);
            MessageListItemTag tag = new MessageListItemTag();
            tag.mSenderTextView = (TextView)view.findViewById(R.id.listitem_blockedsms_list_sender_textview);
            tag.mBodyTextView = (TextView)view.findViewById(R.id.listitem_blockedsms_list_body_textview);
            tag.mTimeSentTextView = (TextView)view.findViewById(R.id.listitem_blockedsms_list_timesent_textview);
            view.setTag(tag);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            if (mColumns == null) {
                mColumns = BlockedSmsLoader.getColumns(cursor);
            }

            MessageListItemTag tag = (MessageListItemTag)view.getTag();
            SmsMessageData messageData = BlockedSmsLoader.getMessageData(cursor, mColumns, tag.mMessageData);
            tag.mMessageData = messageData;

            tag.mSenderTextView.setText("Sender: " + messageData.getSender());
            tag.mBodyTextView.setText("Body: " + messageData.getBody());

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(messageData.getTimeSent());
            String timeSentStr = dateFormat.format(calendar.getTime());
            tag.mTimeSentTextView.setText("Sent at: " + timeSentStr);
        }
    }

    private static final String TAG = BlockedSmsListActivity.class.getSimpleName();

    private BlockedSmsAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blockedsms_list);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        LoaderManager loaderManager = getLoaderManager();
        loaderManager.initLoader(0, null, this);

        BlockedSmsAdapter adapter = new BlockedSmsAdapter(this);
        setListAdapter(adapter);
        mAdapter = adapter;

        registerForContextMenu(getListView());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_blockedsms_list, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finishTryTransition();
            return true;
        case R.id.menu_item_create_test:
            createTestSms();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_blockedsms_list, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        long rowId = info.id;
        switch (item.getItemId()) {
        case R.id.contextmenu_blockedsms_list_restore:
            if (restoreSms(rowId)) {
                Toast.makeText(this, "Message restored to inbox", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Message could not be restored", Toast.LENGTH_SHORT).show();
            }
            return true;
        case R.id.contextmenu_blockedsms_list_delete:
            deleteSms(rowId);
            Toast.makeText(this, R.string.message_deleted, Toast.LENGTH_SHORT).show();
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] from = {
            NekoSmsContract.Blocked._ID,
            NekoSmsContract.Blocked.SENDER,
            NekoSmsContract.Blocked.BODY,
            NekoSmsContract.Blocked.TIME_SENT,
            NekoSmsContract.Blocked.TIME_RECEIVED
        };
        Uri uri = NekoSmsContract.Blocked.CONTENT_URI;
        return new CursorLoader(this, uri, from, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.changeCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.changeCursor(null);
    }

    private void finishTryTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAfterTransition();
        } else {
            finish();
        }
    }

    private void createTestSms() {
        SmsMessageData message = new SmsMessageData();
        message.setSender("1234567890");
        message.setBody("This is a test");
        message.setTimeReceived(System.currentTimeMillis());
        message.setTimeSent(System.currentTimeMillis());
        ContentValues values = message.serialize();
        ContentResolver contentResolver = getContentResolver();
        contentResolver.insert(NekoSmsContract.Blocked.CONTENT_URI, values);
    }

    private boolean restoreSms(long smsId) {
        Uri messageUri = ContentUris.withAppendedId(NekoSmsContract.Blocked.CONTENT_URI, smsId);
        SmsMessageData messageData = BlockedSmsLoader.loadMessage(this, messageUri);
        ContentValues values = new ContentValues(6);
        values.put(Telephony.Sms.ADDRESS, messageData.getSender());
        values.put(Telephony.Sms.BODY, messageData.getBody());
        values.put(Telephony.Sms.DATE, messageData.getTimeReceived());
        values.put(Telephony.Sms.DATE_SENT, messageData.getTimeSent());
        values.put(Telephony.Sms.READ, 1);
        values.put(Telephony.Sms.SEEN, 1);
        ContentResolver contentResolver = getContentResolver();
        Uri inboxMessageUri = contentResolver.insert(Telephony.Sms.CONTENT_URI, values);
        long inboxMessageId = ContentUris.parseId(inboxMessageUri);
        if (inboxMessageId == 0) {
            Xlog.e(TAG, "Failed to write message to inbox, does app have OP_WRITE_SMS permission?");
            return false;
        } else {
            deleteSms(smsId);
            return true;
        }
    }

    private void deleteSms(long smsId) {
        ContentResolver contentResolver = getContentResolver();
        Uri messagesUri = NekoSmsContract.Blocked.CONTENT_URI;
        Uri messageUri = ContentUris.withAppendedId(messagesUri, smsId);
        int deletedRows = contentResolver.delete(messageUri, null, null);
        // TODO: Check return value
    }
}
