package com.sequencing.weather.preference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.sequencing.weather.R;
import com.sequencing.weather.helper.PhoneCodes;
import com.sequencing.weather.helper.ValidationHelper;

import java.util.Arrays;
import java.util.Locale;

public class PhoneNumberPreference extends DialogPreference implements AdapterView.OnItemSelectedListener {
    private String phoneNumber;
    private Spinner spinner;
    private EditText editText;
    private boolean isFirstOpenSpinner = true;

    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());

    private static final String TAG = "PhoneNumberPreference";
    private TextView invalidPhoneMessage;

    public PhoneNumberPreference(Context ctxt, AttributeSet attrs) {
        super(ctxt, attrs);
    }

    @Override
    protected View onCreateDialogView() {
        return null;
    }

    @Override
    protected void onClick() {
        isFirstOpenSpinner = true;

        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        RelativeLayout llDialogLayout = (RelativeLayout) inflater.inflate(R.layout.phone_number_layout, null);

        spinner = (Spinner) llDialogLayout.findViewById(R.id.sCountryeCode);
        invalidPhoneMessage = (TextView) llDialogLayout.findViewById(R.id.tvInvalidPhone);

        final ArrayAdapter<String> adp = new ArrayAdapter<>(getContext(),
                R.layout.spinner_item_text2, PhoneCodes.COUNTRY_NAMES);
        adp.setDropDownViewResource(android.R.layout.select_dialog_singlechoice);

        spinner.setAdapter(adp);
        String countryCode = getUserCountry(getContext());
        int codeIndex = Arrays.asList(PhoneCodes.COUNTRY_CODES).indexOf(countryCode);
        spinner.setSelection(settings.getInt("phone_code_position", codeIndex));
        spinner.setOnItemSelectedListener(this);

        editText = (EditText) llDialogLayout.findViewById(R.id.etPhone);
        editText.addTextChangedListener(new PhoneModifyListener());

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.Theme_AlertStyle);
        builder.setPositiveButton("Done", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Do nothing here because we override this button later to change the close behaviour.
                //However, we still need this because on older versions of Android unless we
                //pass a handler the button doesn't get instantiated
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                onDialogClosed(false);
            }
        });
        builder.setTitle("Enter a phone number");
        builder.setView(llDialogLayout);


        final AlertDialog alertDialog =  builder.create();
        alertDialog.show();

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                phoneNumber = editText.getText().toString().trim();
                if(!ValidationHelper.isValidMobile(phoneNumber)){
                    invalidPhoneMessage.setVisibility(View.VISIBLE);
                } else {
                    onDialogClosed(true);
                    invalidPhoneMessage.setVisibility(View.GONE);
                    alertDialog.dismiss();
                }
            }
        });
        editText.setText(settings.getString("phone_number", ""));
    }


    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            if (callChangeListener(phoneNumber)) {
                persistString(phoneNumber);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            if (defaultValue == null) {
                phoneNumber = getPersistedString("");
            } else {
                phoneNumber = getPersistedString(defaultValue.toString());
            }
        } else {
            phoneNumber = defaultValue.toString();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(!isFirstOpenSpinner) {
            editText.setText("+" + PhoneCodes.COUNTRY_AREA_CODES[position]);
            settings.edit().putInt("phone_code_position", position).commit();
        }
        editText.setSelection(editText.getText().length());
        isFirstOpenSpinner = false;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    public static String getUserCountry(Context context) {
        try {
            final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final String simCountry = tm.getSimCountryIso();
            if (simCountry != null && simCountry.length() == 2) { // SIM country code is available
                return simCountry.toUpperCase(Locale.US);
            } else if (tm.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) { // device is not 3G (would be unreliable)
                String networkCountry = tm.getNetworkCountryIso();
                if (networkCountry != null && networkCountry.length() == 2) { // network country code is available
                    return networkCountry.toUpperCase(Locale.US);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failure to obtain user country");
        }
        return "US";
    }

    private class PhoneModifyListener implements TextWatcher {
        private CharSequence beforeText;
        private boolean cancel = false;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            beforeText = s;
            int codeLength = PhoneCodes.COUNTRY_AREA_CODES[spinner.getSelectedItemPosition()].length() + 1;

            if(start < codeLength && (count == 1 || after == 1))
                cancel = true;

            editText.removeTextChangedListener(this);
            if(cancel) {
                editText.setText(beforeText);
                editText.setSelection(beforeText.length());
                cancel = false;
            }
            editText.addTextChangedListener(this);
        }

        @Override
        public void onTextChanged(final CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if(!ValidationHelper.isValidMobile(editable.toString().trim())){
                invalidPhoneMessage.setVisibility(View.VISIBLE);
            } else {
                invalidPhoneMessage.setVisibility(View.GONE);
            }
        }
    }
}

