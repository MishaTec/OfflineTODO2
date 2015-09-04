package com.example.michael.offlinetodo;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.FindCallback;

import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;

import com.parse.ParseQueryAdapter;
import com.parse.ParseUser;
import com.parse.SaveCallback;
import com.parse.ui.ParseLoginBuilder;

/**
 * TODO:
 * bugs:
 * e: invalid session token, in debt.saveInBackground() in syncDebtsToParse
 * rec: logout, add debt(?), login
 * con: remains draft
 * <p/>
 * q:  Why do i need to create anonymous? Makes junk!
 */
public class DebtListActivity extends Activity {

    private static final int LOGIN_ACTIVITY_CODE = 100;
    private static final int EDIT_ACTIVITY_CODE = 200;

    // Adapter for the Debts Parse Query
    private ParseQueryAdapter<Debt> debtListAdapter;

    private LayoutInflater inflater;

    // For showing empty and non-empty debt views
    private ListView debtListView;
    private LinearLayout noDebtsView;

    private TextView loggedInInfoView;

    private boolean isShowLoginOnFail = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debt_list);

        // Set up the views
        debtListView = (ListView) findViewById(R.id.debt_list_view);
        noDebtsView = (LinearLayout) findViewById(R.id.no_debts_view);
        debtListView.setEmptyView(noDebtsView);
        loggedInInfoView = (TextView) findViewById(R.id.loggedin_info);

        // Set up the Parse query to use in the adapter
        ParseQueryAdapter.QueryFactory<Debt> factory = new ParseQueryAdapter.QueryFactory<Debt>() {
            public ParseQuery<Debt> create() {
                ParseQuery<Debt> query = Debt.getQuery();
                query.orderByAscending("createdAt");
                query.fromLocalDatastore();
                return query;
            }
        };
        // Set up the adapter
        inflater = (LayoutInflater) this
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        debtListAdapter = new DebtListAdapter(this, factory);

        // Attach the query adapter to the view
        ListView debtListView = (ListView) findViewById(R.id.debt_list_view);
        debtListView.setAdapter(debtListAdapter);

        debtListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Debt debt = debtListAdapter.getItem(position);
                openEditView(debt);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ParseUser curr = ParseUser.getCurrentUser();
        // Check if we have a real user
        if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
            // Sync data to Parse
            syncDebtsToParse(false);
            // Update the logged in label info
            updateLoggedInInfo();
        }
    }

    private void updateLoggedInInfo() {
        ParseUser curr = ParseUser.getCurrentUser();
        String token = curr.getSessionToken();
        String authData = curr.getString("authData");
        boolean isLinked = ParseAnonymousUtils.isLinked(curr);

        ParseQuery<Debt> query = Debt.getQuery();
        query.whereEqualTo("author", ParseUser.getCurrentUser());
        query.findInBackground(new FindCallback<Debt>() {
            public void done(List<Debt> debts, ParseException e) {
                if (e == null) {
                    ParseObject.pinAllInBackground((List<Debt>) debts,
                            new SaveCallback() {
                                public void done(ParseException e) {
                                    if (e == null) {
                                        if (!isFinishing()) {
                                            debtListAdapter.loadObjects();
                                        }
                                    } else {
                                        Log.i("DebtListActivity",
                                                "Error pinning debts: "
                                                        + e.getMessage());
                                    }
                                }
                            });
                } else {
                    Log.i("DebtListActivity",
                            "loadFromParse: Error finding pinned debts: "
                                    + e.getMessage());
                }
            }
        });
        String info = "\nauthData: "+authData+"\ntoken: "+token+"\nisLinked: "+isLinked;
        if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
            ParseUser currentUser = ParseUser.getCurrentUser();
            loggedInInfoView.setText(getString(R.string.logged_in,
                    currentUser.getString("name"))+info);
        } else {
            loggedInInfoView.setText(getString(R.string.not_logged_in)+info);// TODO: 04/09/2015 remove info
        }
    }

    private void openEditView(Debt debt) {
        Intent i = new Intent(this, EditDebtActivity.class);
        i.putExtra("ID", debt.getUuidString());
        startActivityForResult(i, EDIT_ACTIVITY_CODE);
    }

    public void openLoginView(Context context) {
        ParseLoginBuilder builder = new ParseLoginBuilder(context);
        if (ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {// TODO: 02/09/2015 remove all next
//            ParseUser.logOut();
//            ParseUser.deleteInBackground();
        }
        ParseUser curr = ParseUser.getCurrentUser();
        String tokenBefore = curr.getSessionToken();
        boolean isStillLinked = ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser());
        System.out.println("******************************************************************* token before: " + tokenBefore + ", isLinked: " + isStillLinked);
        startActivityForResult(builder.build(), LOGIN_ACTIVITY_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // An OK result means the pinned dataset changed or
        // log in was successful
        if (resultCode == RESULT_OK) {
            if (requestCode == EDIT_ACTIVITY_CODE) {
                // Coming back from the edit view, update the view
                debtListAdapter.loadObjects();
            } else if (requestCode == LOGIN_ACTIVITY_CODE) {
                // If the user is new, sync data to Parse,
                // else get the current list from Parse
                if (ParseUser.getCurrentUser().isNew()) {// TODO: 03/09/2015 changed
                    syncDebtsToParse(true);
                } else {
                    loadFromParse();
                }
            }

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.debt_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_new) {
            // Make sure there's a valid user, anonymous
            // or regular
            if (ParseUser.getCurrentUser() != null) {
                startActivityForResult(new Intent(this, EditDebtActivity.class),
                        EDIT_ACTIVITY_CODE);
            }
        }

        if (item.getItemId() == R.id.action_sync) {
            syncDebtsToParse(true);
        }

        if (item.getItemId() == R.id.action_logout) {
            ParseUser curr = ParseUser.getCurrentUser();// TODO: 03/09/2015 remove
            // Log out the current user
            ParseUser.logOut();
//            this.recreate();// TODO: 02/09/2015
            // Create a new anonymous user
//            ParseAnonymousUtils.logIn(null);// FIXME: 02/09/2015
            // Update the logged in label info
            updateLoggedInInfo();
            // Clear the view
            debtListAdapter.clear();
            // Unpin all the current objects
            ParseObject
                    .unpinAllInBackground(DebtListApplication.DEBT_GROUP_NAME);
        }

        if (item.getItemId() == R.id.action_login) {
            openLoginView(this);
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean isRealUser = !ParseAnonymousUtils.isLinked(ParseUser
                .getCurrentUser());
        menu.findItem(R.id.action_login).setVisible(!isRealUser);
        menu.findItem(R.id.action_logout).setVisible(isRealUser);
        return true;
    }

    private void syncDebtsToParse(final boolean isShowLoginOnFail) {
        // We could use saveEventually here, but we want to have some UI
        // around whether or not the draft has been saved to Parse
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if ((ni != null) && (ni.isConnected())) {
            if (!ParseAnonymousUtils.isLinked(ParseUser.getCurrentUser())) {
                // If we have a network connection and a current logged in user, sync the debts
                // In this app, local changes should overwrite content on the server.

                ParseQuery<Debt> query = Debt.getQuery();
                query.fromPin(DebtListApplication.DEBT_GROUP_NAME);
                query.whereEqualTo("isDraft", true);
                query.findInBackground(new FindCallback<Debt>() {
                    public void done(List<Debt> debts, ParseException e) {
                        if (e == null) {
                            for (final Debt debt : debts) {
                                // Set is draft flag to false before
                                // syncing to Parse
                                debt.setDraft(false);
                                debt.saveInBackground(new SaveCallback() {// FIXME: 04/09/2015

                                    @Override
                                    public void done(ParseException e) {
                                        if (e == null) {
                                            // Let adapter know to update view
                                            if (!isFinishing()) {
                                                debtListAdapter.notifyDataSetChanged();
                                            }
                                        } else {
                                            if(!isShowLoginOnFail) {
                                                Toast.makeText(getApplicationContext(),
                                                        e.getMessage(),
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                            System.out.println("########################################\n##################################\n############\n"+e.getMessage()+"\n##############\n############################");
                                            // Reset the is draft flag locally to true
                                            debt.setDraft(true);
                                            // Save flag field as late as possible - to deal with
                                            // asynchronous callback
                                            DebtListActivity.this.isShowLoginOnFail = isShowLoginOnFail;
                                            handleParseError(e);
//openLoginView(DebtListActivity.this);
//											e.printStackTrace();// TODO: 02/09/2015 remove with next
                                            ParseUser curr = ParseUser.getCurrentUser();
                                            boolean isAnonymous = ParseAnonymousUtils.isLinked(curr);
                                            String token = curr.getSessionToken();
//                                            System.out.println();
//                                            e.printStackTrace();

                                        }
                                    }

                                });
                            }
                        } else {
                            Log.i("DebtListActivity",
                                    "syncDebtsToParse: Error finding pinned debts: "
                                            + e.getMessage());
                        }
                    }
                });
            } else {
                // If we have a network connection but no logged in user, direct
                // the person to log in or sign up.
                openLoginView(this);
            }
        } else {
            // If there is no connection, let the user know the sync didn't
            // happen
            Toast.makeText(
                    getApplicationContext(),
                    "Your device appears to be offline. Some debts may not have been synced to Parse.",
                    Toast.LENGTH_LONG).show();
        }

    }

    private void loadFromParse() {
        ParseQuery<Debt> query = Debt.getQuery();
        query.whereEqualTo("author", ParseUser.getCurrentUser());
        query.findInBackground(new FindCallback<Debt>() {
            public void done(List<Debt> debts, ParseException e) {
                if (e == null) {
                    ParseObject.pinAllInBackground((List<Debt>) debts,
                            new SaveCallback() {
                                public void done(ParseException e) {
                                    if (e == null) {
                                        if (!isFinishing()) {
                                            debtListAdapter.loadObjects();
                                        }
                                    } else {
                                        Log.i("DebtListActivity",
                                                "Error pinning debts: "
                                                        + e.getMessage());
                                    }
                                }
                            });
                } else {
                    Log.i("DebtListActivity",
                            "loadFromParse: Error finding pinned debts: "
                                    + e.getMessage());
                }
            }
        });
    }

    private class DebtListAdapter extends ParseQueryAdapter<Debt> {

        public DebtListAdapter(Context context,
                               QueryFactory<Debt> queryFactory) {
            super(context, queryFactory);
        }

        @Override
        public View getItemView(Debt debt, View view, ViewGroup parent) {
            ViewHolder holder;
            if (view == null) {
                view = inflater.inflate(R.layout.list_item_debt, parent, false);
                holder = new ViewHolder();
                holder.debtTitle = (TextView) view
                        .findViewById(R.id.debt_title);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }
            TextView debtTitle = holder.debtTitle;
            debtTitle.setText(debt.getTitle());
            if (debt.isDraft()) {
                debtTitle.setTypeface(null, Typeface.ITALIC);
                debtTitle.setTextColor(Color.RED);// TODO: 02/09/2015 GRAY

            } else {
                debtTitle.setTypeface(null, Typeface.NORMAL);
                debtTitle.setTextColor(Color.BLACK);
            }
            return view;
        }
    }

    private static class ViewHolder {
        TextView debtTitle;
    }


    public void handleParseError(ParseException e) {
        switch (e.getCode()) {
            case ParseException.INVALID_SESSION_TOKEN:
                handleInvalidSessionToken();
                break;

            // Other Parse API errors
        }
    }

    private void handleInvalidSessionToken() {// TODO: 04/09/2015 remove arg
        //--------------------------------------
        // Option 1: Show a message asking the user to log out and log back in.
        //--------------------------------------
        // If the user needs to finish what they were doing, they have the opportunity to do so.
        //
        // new AlertDialog.Builder(getActivity())
        //   .setMessage("Session is no longer valid, please log out and log in again.")
        //   .setCancelable(false).setPositiveButton("OK", ...).create().show();

        //--------------------------------------
        // Option #2: Show login screen so user can re-authenticate.
        //--------------------------------------
        // You may want this if the logout button could be inaccessible in the UI.
        //
        // startActivityForResult(new ParseLoginBuilder(getActivity()).build(), 0);
        if (isShowLoginOnFail) {
            // only in case the user initiated the sync - no demanding login
            openLoginView(this);
        }
        else{
            Toast.makeText(getApplicationContext(),
                    "Sync failed",
                    Toast.LENGTH_SHORT).show();
        }
    }
}

