package nl.q42.huelimitededition.activities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import nl.q42.huelimitededition.R;
import nl.q42.huelimitededition.Util;
import nl.q42.huelimitededition.models.Bridge;
import nl.q42.huelimitededition.widgets.LightsWidgetProvider;
import nl.q42.javahueapi.HueService;
import nl.q42.javahueapi.models.Light;
import android.app.ActionBar;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class LightsWidgetConfigActivity extends Activity {
	private Bridge bridge;
	private HueService service;
	
	private RelativeLayout loader, abLoader;
	private LinearLayout content;
	private LinearLayout lightsList;
	private Button createButton;
	
	private HashMap<String, Light> lights = new HashMap<String, Light>();
	private HashMap<String, CheckBox> lightViews = new HashMap<String, CheckBox>();
	
	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_widget_lights_config);
		
		ActionBar ab = getActionBar();
		ab.setCustomView(R.layout.loader);
		ab.setDisplayShowCustomEnabled(true);
		
		abLoader = (RelativeLayout) ab.getCustomView();
		abLoader.findViewById(R.id.loader_refresh).setVisibility(View.GONE);
		abLoader.findViewById(R.id.loader_spinner).setVisibility(View.VISIBLE);
		
		loader = (RelativeLayout) findViewById(R.id.widget_lights_config_loader);
		content = (LinearLayout) findViewById(R.id.widget_lights_config_content);
		lightsList = (LinearLayout) findViewById(R.id.widget_lights_config_lights);
		
		createButton = (Button) findViewById(R.id.widget_lights_config_create);
		createButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				createWidget();
			}
		});
		
		// Select a light from the bridge currently used in the main app
		bridge = Util.getLastBridge(this);
		
		if (bridge == null) {
			Toast.makeText(this, getString(R.string.widget_config_error_bridge), Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		
		service = new HueService(bridge.getIp(), Util.getDeviceIdentifier(this));
		
		// Load list of lights
		if (savedInstanceState == null) {
			loadLights();
		} else {
			lights = (HashMap<String, Light>) savedInstanceState.getSerializable("lights");
			addLights();
		}
		
		checkConditions();
		
		// Make sure this is set unless the create button is pressed
		setResult(RESULT_CANCELED);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		
		state.putSerializable("lights", lights);
	}
	
	private Set<String> getSelectedLights() {
		Set<String> selectedLights = new HashSet<String>();
		
		for (String id : lights.keySet()) {
			if (lightViews.get(id).isChecked()) {
				selectedLights.add(id);
			}
		}
		
		return selectedLights;
	}
	
	private void createWidget() {
		Bundle extras = getIntent().getExtras();
		int widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		
		AppWidgetManager widgetManager = AppWidgetManager.getInstance(this);
		ComponentName widget = new ComponentName(getPackageName(), LightsWidgetConfigActivity.class.getName());
		int[] widgetIds = widgetManager.getAppWidgetIds(widget);
		
		// Store the configuration for this widget
		SharedPreferences.Editor prefsEdit = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
		prefsEdit.putString("widget_" + widgetId + "_ip", bridge.getIp());
		prefsEdit.putStringSet("widget_" + widgetId + "_ids", getSelectedLights());
		prefsEdit.commit();
		
		// Send initial update request
		Intent initialUpdate = new Intent(this, LightsWidgetProvider.class);
		initialUpdate.setAction("android.appwidget.action.APPWIDGET_UPDATE");
		initialUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
		sendBroadcast(initialUpdate);
		
		Intent result = new Intent();
		result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
		setResult(RESULT_OK, result);
		finish();
	}
	
	private void loadLights() {
		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... params) {
				try {
					lights = new HashMap<String, Light>(service.getLights());
				} catch (Exception e) {
					return false;
				}
				
				return true;
			}
			
			@Override
			protected void onPostExecute(Boolean result) {
				if (result) {					
					addLights();
				} else {
					Toast.makeText(
							LightsWidgetConfigActivity.this,
							getString(R.string.widget_config_error_connection),
							Toast.LENGTH_SHORT).show();
					
					finish();
				}
			}
		}.execute();
	}
	
	private void checkConditions() {
		boolean tooMany = getSelectedLights().size() > 6;
		boolean tooFew = getSelectedLights().size() == 0;
		createButton.setEnabled(!tooMany && !tooFew);
		
		if (tooMany) {
			createButton.setText(R.string.widget_lights_config_error_too_many);
		} else if (tooFew) {
			createButton.setText(R.string.widget_lights_config_error_too_few);
		} else {
			createButton.setText(R.string.widget_config_create);
		}
	}
	
	private void addLights() {
		View lastView = null;
		
		for (String id : Util.getSortedLights(lights)) {
			lastView = getLayoutInflater().inflate(R.layout.widget_lights_config_light, lightsList, false);
			
			CheckBox cb = (CheckBox) lastView.findViewById(R.id.widget_lights_config_light_name);
			cb.setText(lights.get(id).name);
			cb.setId(id.hashCode());
			
			cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					checkConditions();
				}
			});
			
			lightViews.put(id, cb);
			lightsList.addView(lastView);
		}
		
		if (lastView != null) {
			lastView.findViewById(R.id.widget_lights_config_light_divider).setVisibility(View.GONE);
		}
		
		abLoader.findViewById(R.id.loader_spinner).setVisibility(View.GONE);
		loader.setVisibility(View.GONE);
		content.setVisibility(View.VISIBLE);
	}
}
