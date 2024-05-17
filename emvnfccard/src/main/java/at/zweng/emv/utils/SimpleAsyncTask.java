package at.zweng.emv.utils;

import android.os.AsyncTask;
import android.util.Log;

/**
 * Simple Async task
 *
 * @author Millau Julien
 */
public abstract class SimpleAsyncTask extends AsyncTask<Void, Void, Object> {

    @Override
    protected Object doInBackground(final Void... params) {

        Object result = null;

        try {
            doInBackground();
        } catch (Exception e) {
            result = e;
            Log.e("SimpleAsyncTask", e.toString());
        }

        return result;
    }

    protected abstract void doInBackground();

}
