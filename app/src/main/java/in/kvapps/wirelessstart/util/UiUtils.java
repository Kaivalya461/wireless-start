package in.kvapps.wirelessstart.util;

import android.text.InputFilter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import in.kvapps.wirelessstart.data.PreferenceManager;
import in.kvapps.wirelessstart.enums.DurationOption;

public class UiUtils {
    private static final long MAX_SAFE_START_MS = 5000;

    public static void setButtonState(View button, boolean isEnabled, float alpha) {
        button.setEnabled(isEnabled);
        button.setAlpha(alpha);
    }

    public static void setupDurationSpinner(android.content.Context context, Spinner spinner, EditText customInput, PreferenceManager preferenceManager) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, DurationOption.getLabels());
        spinner.setAdapter(adapter);

        // Fixed reference from inputCustomStart to customInput
        spinner.setSelection(preferenceManager.getStartSpinnerPosition());
        customInput.setText(preferenceManager.getStartCustomMs());

        // Limit maximum input length to 4 digits (e.g., up to "5000")
        customInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                customInput.setVisibility(position == 3 ? View.VISIBLE : View.GONE);
                preferenceManager.saveStartSpinnerPosition(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        customInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String inputStr = s.toString().trim();
                if (!inputStr.isEmpty()) {
                    try {
                        int value = Integer.parseInt(inputStr);
                        if (value > 5000) {
                            // Automatically clamp the text down to 5000 if it exceeds the limit
                            customInput.setText(String.valueOf(MAX_SAFE_START_MS));
                            customInput.setSelection(customInput.getText().length()); // Keep cursor at the end

                            Toast.makeText(context, "Maximum limit is 5000ms", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                preferenceManager.saveStartCustomMs(inputStr);
            }
        });
    }
}