package at.oerp.pos.cordova;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import at.oerp.pos.NoInitException;
import at.oerp.pos.PosHwDisplay;
import at.oerp.pos.PosHwPrinter;
import at.oerp.pos.PosHwScale;
import at.oerp.pos.PosHwScan;
import at.oerp.pos.PosHwService;
import at.oerp.pos.PosHwSmartCard;
import at.oerp.pos.PosReceipt;
import at.oerp.pos.WeightResult;

public class PosHwPlugin extends CordovaPlugin {
	
	private String TAG = "PosHwPlugin";
	private PosHwService service;
	private HashMap<String, PosHwPluginCmd> api;
	private ActivityCallback activityCallback;
	@SuppressLint("SimpleDateFormat")
	private SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
		
	abstract static class PosHwPluginCmd {
		abstract boolean execute(final JSONArray args, final CallbackContext callbackContext) 
					throws Exception;		
	}
	
	abstract class AsyncHwPluginCmd extends PosHwPluginCmd implements Runnable {
		boolean execute(final JSONArray args, final CallbackContext callbackContext)  
				throws Exception {
			cordova.getThreadPool().execute(this);
			callbackContext.success();
			return true;
		}
	}
	
	abstract static class ActivityCallback {
		CallbackContext callbackContext;
		public ActivityCallback(CallbackContext inCallbackContext) {
			callbackContext = inCallbackContext;
		}
		public void onActivityResult(int resultCode, Intent intent) {
			
		}
	}
	
	
	protected void pushCallback(Class<? extends Activity> inActivityClass, ActivityCallback inCallback) {
		boolean valid = false; 
		try {
			Intent indent = new Intent(cordova.getActivity().getApplicationContext(), inActivityClass);			
			cordova.setActivityResultCallback(this);
			activityCallback = inCallback;
			cordova.startActivityForResult((CordovaPlugin) this, indent, activityCallback.hashCode());
			valid = true;
		} finally {
			if ( !valid ) {
				activityCallback = null;
			}
		}
	}
	
	
	@Override
	public boolean execute(final String inAction, final JSONArray inArgs, final CallbackContext inCallbackContext) throws JSONException {
		try {

			// create service
			synchronized ( this ) {
				
				// init service
				if ( service == null ) {
					service = PosHwService.create(cordova.getActivity().getApplication());	
					if ( service != null ) {
						service.open();
					}

					// init api
					api = new HashMap<String, PosHwPlugin.PosHwPluginCmd>();
					api.put("getStatus", new PosHwPluginCmd() {
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							JSONObject status = new JSONObject();
							status.put("manufacturer", Build.MANUFACTURER);
							status.put("model", Build.MODEL);
							
							// check printer
							PosHwPrinter printer = service.getPrinter();
							if ( printer != null ) {
								JSONObject printerStatus = new JSONObject();
								printerStatus.put("installed", "true");
								printerStatus.put("type", printer.getType());
								status.put("printer", printerStatus);
							}
							// check sale
							PosHwScale scale = service.getScale();
							if ( scale != null ) {
								JSONObject scaleStatus = new JSONObject();
								scaleStatus.put("supported", "true");
								status.put("scale", scaleStatus);
							}
							// check display
							PosHwDisplay display = service.getCustomerDisplay();
							if ( display != null ) {
								JSONObject displayStatus = new JSONObject();
								displayStatus.put("installed", true);
								displayStatus.put("lines", display.getLines());
								displayStatus.put("chars", display.getCharsPerLine());
								displayStatus.put("fullcharset", display.fullCharset());
								status.put("display", displayStatus);
							}
							// check numpad
							status.put("numpad", service.hasNumpad());
							// check scanner
							status.put("scanner", service.hasScanner());
							// check cardreader
							status.put("cardreader", service.hasCardReader());
							// notify status
							callbackContext.success(status);
							return true;
						}
					});
					
					api.put("printHtml", new PosHwPluginCmd() {
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws JSONException, IOException {
							String html = args.getString(0);
							if ( html != null && html.length() > 0) {
								service.getPrinter().printHtml(html);
								callbackContext.success(html);
							}
							callbackContext.success();
							return true;
						}
					});
					
					api.put("scaleInit", new PosHwPluginCmd() {
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							float price = (float) args.getDouble(0);
							float tara = (float) args.getDouble(1);
							if ( service.getScale().init(price, tara) ) {
								callbackContext.success();
							} else {
								callbackContext.error("init failed");
							}
							return true;
						}
					});
					
					api.put("scaleRead", new PosHwPluginCmd() {
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							WeightResult result = new WeightResult();
							if ( service.getScale().readResult(result) ) {
								JSONObject res = new JSONObject();
								res.put("weight", result.weight);
								res.put("price", result.price);
								res.put("total", result.total);
								callbackContext.success(res);
							} else {
								callbackContext.error("no data");
							}
							return true;
						}
					});
					
					api.put("display", new PosHwPluginCmd() {
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							Object arg = args.get(0);
							if ( arg == null ) {
								service.getCustomerDisplay().setDisplay();
							} else if ( arg instanceof String ) {
								service.getCustomerDisplay().setDisplay(arg.toString());	
							} else if ( args instanceof JSONArray) {
								JSONArray argLines = (JSONArray) args;
								String[] displayLines = new String[argLines.length()];
								for ( int i=0; i<displayLines.length; i++ )
									displayLines[i] = argLines.getString(i);
								service.getCustomerDisplay().setDisplay(displayLines);					
							} else {
								service.getCustomerDisplay().setDisplay();
							}
							callbackContext.success();
							return true;
						}
					});
					
					api.put("openCashDrawer", new AsyncHwPluginCmd() {
						
						@Override
						public void run() {
							try {
								service.openCashDrawer();
							} catch (IOException e) {
								Log.e(TAG,e.getMessage(),e);
							}
							
						}
					});
					
					api.put("openExternCashDrawer", new AsyncHwPluginCmd() {
						
						@Override
						public void run() {
							service.openExternCashDrawer();							
						}
					});
					
					api.put("test", new PosHwPluginCmd() {
						
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							callbackContext.success("Test OK!");
							return true;
						}
					});
					
					api.put("provisioning", new PosHwPluginCmd() {
						
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							service.provisioning();
							callbackContext.success();
							return true;
						}
					});
					
					api.put("scan", new PosHwPluginCmd() {
						
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							if ( !service.hasScanner() ) return false;

							// prepare callback
							pushCallback( service.getScanActivity(), new ActivityCallback(callbackContext) {
								@Override
								public void onActivityResult(int resultCode, Intent intent) {
									try {
										JSONObject result = new JSONObject();
										if ( resultCode == Activity.RESULT_OK ) {
										   result.put("canceled", false);
										   result.put("text", intent.getStringExtra(PosHwScan.RESULT_TEXT));
										   result.put("format", intent.getStringExtra(PosHwScan.RESULT_FORMAT));
										} else {
										   result.put("canceled", true);
										}
										callbackContext.success(result);
									} catch ( JSONException e) {
										callbackContext.error(e.getMessage());
									}								
								}
							});
							
							return true; 
						}
					});
					
					api.put("signTest", new PosHwPluginCmd() {
						
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							PosHwSmartCard smartCard = service.getSmartCard();
							if ( smartCard == null ) return false;
							String result = smartCard.test();
							callbackContext.success(result);
							return true;
						}
					});
					
					api.put("signInit", new PosHwPluginCmd() {

						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							PosHwSmartCard smartCard = service.getSmartCard();
							if ( smartCard == null ) return false;
							JSONObject config = args.getJSONObject(0);
							smartCard.init(config.getString("sign_key"));
							callbackContext.success();
							return true;
						}
					});
					
					api.put("sign", new PosHwPluginCmd() {
						
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							PosHwSmartCard smartCard = service.getSmartCard();
							if ( smartCard == null ) return false;
							
							JSONObject jsonReceipt = args.getJSONObject(0);
							
							// convert
							PosReceipt receipt = new PosReceipt();							
							receipt.cashBoxID = jsonReceipt.getString("sign_pid");
							receipt.receiptIdentifier = Long.toString(jsonReceipt.getLong("seq"));
							receipt.receiptDateAndTime = dateTimeFormat.parse(jsonReceipt.getString("date"));
							receipt.sumTaxSetNormal = jsonReceipt.getDouble("amount");
							receipt.sumTaxSetErmaessigt1 = jsonReceipt.getDouble("amount_1");
							receipt.sumTaxSetErmaessigt2 = jsonReceipt.getDouble("amount_2");
							receipt.sumTaxSetNull = jsonReceipt.getDouble("amount_0");
							receipt.sumTaxSetBesonders = jsonReceipt.getDouble("amount_s");
							receipt.turnover = jsonReceipt.getDouble("turnover");
							receipt.signatureCertificateSerialNumber = jsonReceipt.getString("sign_serial");
							receipt.prevCompactData = jsonReceipt.getString("last_dep");
							
							// check if hash build be build
							PosHwPrinter printer = service.getPrinter();							
							receipt.buildHash = printer != null && printer.textOnly();
									
							// evaluate special type
							String st = jsonReceipt.optString("st", null);
							if ( st != null ) {
								if ( "c".equalsIgnoreCase(st)) {
									receipt.specialType = "STO";
								} else if ( "t".equalsIgnoreCase(st) ) {
									receipt.specialType = "TRA";
								} else if ( "s".equalsIgnoreCase(st) ) {
									receipt.first = true;
								}
							}
							
							// sign
							smartCard.signReceipt(receipt);
							
							// build result
							jsonReceipt.put("turnover_enc", receipt.encryptedTurnoverValue);
							jsonReceipt.put("qr", receipt.plainData);
							jsonReceipt.put("dep", receipt.compactData);
							jsonReceipt.put("sig", receipt.valid);
							jsonReceipt.putOpt("hs", receipt.hashData);
							callbackContext.success(jsonReceipt);
							
							return true;
						}
					});
					
					api.put("signQueryCert", new PosHwPluginCmd() {
						
						@Override
						boolean execute(JSONArray args, CallbackContext callbackContext) throws Exception {
							PosHwSmartCard smartCard = service.getSmartCard();
							if ( smartCard == null ) return false;
							callbackContext.success(smartCard.getCertificate());
							return true;
						}
					});
					
					api.put("beep", new AsyncHwPluginCmd() {
						public void run() {
							try {
								service.beep();
							} catch (IOException e) {
								Log.e(TAG,e.getMessage(),e);
							}
						}
					});
				}
			}

			// no service
			if ( service == null ) {				
				inCallbackContext.error("No Service");
				return true;
			}
			
			// return false if command 
			// not exist
			PosHwPluginCmd cmd = api.get(inAction);
			if ( cmd == null )
				return false;
			
			// execute cmd
			return cmd.execute(inArgs, inCallbackContext);
			
		} catch ( NoInitException e ) {
			inCallbackContext.error("no_init");
			return true;
		} catch ( Throwable e) {
			// log error
			String msg = e.getMessage();
			if ( msg != null ) {
				Log.e(TAG, msg);
			} else {
				msg = e.getClass().getName();
				if ( e.getCause() != null ) {
					msg = e.getCause().getMessage();
					if ( msg == null ) msg = e.getCause().getClass().getName();
				}
				Log.e(TAG, msg);
			}
			
			// throw before return
			if ( e instanceof JSONException ) {				
				throw (JSONException) e;
			}
			
			// return error via callback
			inCallbackContext.error(msg);
			return true;
		}
	}
	
	
	@Override
	public void onStop() {
		synchronized (this) {
			activityCallback = null;
			if( service != null ) {
				try {
					service.close();
				} catch ( Exception e) {
					Log.e(TAG, e.getMessage());
				} finally {
					service = null;
				} 
			}
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		ActivityCallback callback = activityCallback;
		if ( callback != null && requestCode == callback.hashCode() ) { 
			callback.onActivityResult(resultCode, intent);
		} 
	}
	
	
}
