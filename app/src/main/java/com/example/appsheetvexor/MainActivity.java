package com.example.appsheetvexor;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private WebView webView, pdfView;
    private Button btnQr;
    private FrameLayout pdfOverlay;
    private File pdfFileActual = null;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;
    private String lastQrId = null;
    private long lastBackPress = 0;
    private MediaRecorder recorder;
    private File tempAudioFile;
    private Bitmap selectedIconBitmap = null;
    private ImageView previewIconView;
    private final String DATASTUDIO_URL = "https://datastudio.google.com/embed/reporting/a9a7f8c7-b820-4b17-9e6b-b6168d82d175/page/jfW6F";
    private String APPSHEET_URL = "https://www.appsheet.com/start/06effb1c-9afa-464d-9b0e-5db6e583136b?platform=mobile";
    private final String GOOGLE_SHEET_API_URL = "https://script.google.com/macros/s/AKfycbxctlMwBkbUbq5M7yZ_objkvRx_AOmUOoZYz_KM5ItJ0GzGg1jxAhOFIfBas5QCnKKe/exec";
    private final String PAYPAL_LINK = "https://www.paypal.com/ncp/payment/4ADF32MFFTY2N";
    private boolean isLicensed = false;
    private String licensePlan = "";
    private long trialExpiresAt = 0;
    private boolean trialAllowed = false;
    private boolean trialActive = false;
    private int trialDaysLeft = 0;
    private String deviceId = "";
    private int licenseMax = 1;
    private int licenseUsed = 1;
    private boolean primerIngresoCompletado = false; // FIX PARA NO BLOQUEAR LOGIN

    public class Bridge {
        @JavascriptInterface public void setId(String id){ lastQrId = id; }
        @JavascriptInterface public void showBtn(){ runOnUiThread(() -> btnQr.setVisibility(View.VISIBLE)); }
        @JavascriptInterface public void hideBtn(){ runOnUiThread(() -> btnQr.setVisibility(View.GONE)); }
        @JavascriptInterface public void cerrarPdf(){ runOnUiThread(() -> { if(pdfOverlay.getVisibility()==View.VISIBLE) pdfOverlay.setVisibility(View.GONE); }); }
        @JavascriptInterface public void abrirActivar(){ runOnUiThread(() -> mostrarActivarProDialog()); }
        @JavascriptInterface public void crearAcceso(){ runOnUiThread(() -> mostrarDialogCrearAccesoDirecto()); }
        @JavascriptInterface public void estadoPlan(){ runOnUiThread(() -> mostrarEstadoPlanDialog()); }
        @JavascriptInterface public void activarPro(){ runOnUiThread(() -> mostrarActivarProDialog()); }
        @JavascriptInterface public void comprarLicencia(){ runOnUiThread(() -> { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_LINK))); }); }
        @JavascriptInterface public void reiniciarEnCualquierVista(){ /* DESACTIVADO */ }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnQr = findViewById(R.id.btnQr);
        webView = findViewById(R.id.webview);
        pdfOverlay = findViewById(R.id.pdfOverlay);
        pdfView = findViewById(R.id.pdfView);
        String directUrl = getIntent().getStringExtra("direct_url");
        if(directUrl!= null &&!directUrl.isEmpty()){ APPSHEET_URL = directUrl; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 101);
        initVexorLicensing();
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        s.setSupportMultipleWindows(true);
        
        WebSettings ps = pdfView.getSettings();
        ps.setJavaScriptEnabled(true); ps.setAllowFileAccess(true); ps.setAllowUniversalAccessFromFileURLs(true);
        ps.setDomStorageEnabled(true); ps.setBuiltInZoomControls(true); ps.setDisplayZoomControls(false);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        cm.setAcceptThirdPartyCookies(pdfView, true);
        pdfView.setWebViewClient(new WebViewClient(){ @Override public boolean shouldOverrideUrlLoading(WebView view, String url){ view.loadUrl(url); return true; } });
        webView.addJavascriptInterface(new Bridge(), "AndroidQR");
        btnQr.setOnClickListener(v -> abrirScanner());
        TextView btnPdfMenu = findViewById(R.id.btnPdfMenu);
        btnPdfMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(MainActivity.this, v);
            popup.getMenu().add("📂 COMPARTIR");
            popup.getMenu().add("⬇ DESCARGAR");
            popup.getMenu().add("❌ SALIR");
            popup.setOnMenuItemClickListener(item -> {
                String t = item.getTitle().toString();
                if(t.contains("SALIR")){ pdfOverlay.setVisibility(View.GONE); pdfView.loadUrl("about:blank"); }
                if(t.contains("DESCARGAR") && pdfFileActual!=null){ copiarADescargas(pdfFileActual); Toast.makeText(this, "Descargado", Toast.LENGTH_LONG).show(); }
                if(t.contains("COMPARTIR") && pdfFileActual!=null){ Uri uri=FileProvider.getUriForFile(this, getPackageName()+".fileprovider", pdfFileActual); Intent i=new Intent(Intent.ACTION_SEND); i.setType("application/pdf"); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Compartir PDF")); }
                return true;
            }); popup.show();
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r){ r.grant(r.getResources()); }
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p){
                filePathCallback = cb;
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Selecciona una acción");
                builder.setItems(new String[]{"📷 Cámara", "🎤 Grabar Audio", "📁 Selector de medios"}, (dialog, which) -> {
                    if(which==0){
                        Intent take=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        try{
                            File f=File.createTempFile("IMG_"+new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())+"_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                            cameraImageUri=FileProvider.getUriForFile(MainActivity.this, getPackageName()+".fileprovider", f);
                            take.putExtra(MediaStore.EXTRA_OUTPUT,cameraImageUri);
                            startActivityForResult(take, 1003);
                        }catch(Exception e){ if(filePathCallback!=null){ filePathCallback.onReceiveValue(null); filePathCallback=null; } }
                    }else if(which==1){ grabarAudioInterno(); }
                    else{
                        Intent content=new Intent(Intent.ACTION_GET_CONTENT);
                        content.addCategory(Intent.CATEGORY_OPENABLE);
                        content.setType("*/*");
                        content.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*","audio/*","video/*","application/pdf"});
                        startActivityForResult(Intent.createChooser(content, "Elegir archivo"), 1001);
                    }
                });
                builder.setOnCancelListener(d -> { if(filePathCallback!=null){ filePathCallback.onReceiveValue(null); filePathCallback=null; } });
                builder.show();
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url){
                // FIX: Marcar que ya paso el primer ingreso a AppSheet, ahora si activar PDF/URL
                if(url.contains("appsheet.com/start") && !primerIngresoCompletado){
                    // Esperamos 3 segundos después de entrar a AppSheet para activar funciones PDF
                    view.postDelayed(() -> primerIngresoCompletado = true, 3000);
                }
                if(!hasAccess()){
                    view.evaluateJavascript("javascript:(function(){ try{ var els=document.querySelectorAll('a[href*=\"datastudio\"],a[href*=\"lookerstudio\"]'); if(els.length>0){ var c=els[0]; for(var i=0;i<8&&c.parentElement;i++) c=c.parentElement; c.innerHTML='<div style=\"padding:24px;text-align:center;font-family:sans-serif;\"><h3>❌ Prueba terminada</h3><p>Compra licencia de por vida.<br><b>💳 Pago único</b></p><a href=\""+PAYPAL_LINK+"\" target=\"_blank\" style=\"display:inline-block;background:linear-gradient(135deg,#8f6bc0,#3fb0ac);color:#fff;padding:12px 18px;border-radius:8px;text-decoration:none;font-weight:700;margin-top:10px;\">💳 COMPRAR LICENCIA</a><br><br><button onclick=\"window.AndroidQR.abrirActivar()\" style=\"padding:8px 14px;\">🔑 ACTIVAR PRO</button></div>'; } }catch(e){} })()", null);
                }
                view.evaluateJavascript("javascript:(function(){ try{ var all=document.body.innerHTML; if(all.includes('Volver a enviar')){ document.body.innerHTML = all.replace(/Volver a enviar/g,''); } }catch(e){} })()", null);
                String js="javascript:(function(){"
                        + "function toAscii(s){ var out=''; for(var i=0;i<s.length;i++){ var cp=s.codePointAt(i); if(cp>65535){i++;} if(cp>=0x1D400&&cp<=0x1D419) out+=String.fromCharCode(cp-0x1D400+65); else if(cp>=0x1D41A&&cp<=0x1D433) out+=String.fromCharCode(cp-0x1D41A+97); else if(cp>=0x1D5D4&&cp<=0x1D5ED) out+=String.fromCharCode(cp-0x1D5D4+65); else if(cp>=0x1D5EE&&cp<=0x1D607) out+=String.fromCharCode(cp-0x1D5EE+97); else if(cp>=0x1D670&&cp<=0x1D689) out+=String.fromCharCode(cp-0x1D670+65); else if(cp>=0x1D68A&&cp<=0x1D6A3) out+=String.fromCharCode(cp-0x1D68A+97); else if(cp>=0x1D7CE&&cp<=0x1D7D7) out+=String.fromCharCode(cp-0x1D7CE+48); else out+=s[i]; } return out; }"
                        + "function getLabel(el){ var t=''; var p=el; for(var i=0;i<10&&p;i++){ t+=(p.innerText||'')+' '+(p.textContent||'')+' '; p=p.parentElement; } return toAscii(t).toUpperCase(); }"
                        + "function getViewName(){ try{ var h=location.hash.replace(/^#/,''); var sp=new URLSearchParams(h); var v=sp.get('view')||sp.get('viewName')||''; if(v) return v.toUpperCase(); }catch(e){} return ''; }"
                        + "function embeber(){ var l=document.querySelector('a[href*=\"datastudio\"],a[href*=\"lookerstudio\"]'); if(l&&l.dataset.embed!='1'){ l.dataset.embed='1'; var c=l; for(var i=0;i<8&&c.parentElement;i++) c=c.parentElement; c.style.cssText='margin:0;padding:0;border:0;width:100%;height:calc(100vh - 56px);overflow:hidden;'; c.innerHTML='<div style=\"width:100%;height:100%;overflow:hidden;\"><iframe src=\"" + DATASTUDIO_URL + "\" style=\"width:100%;height:calc(100% + 20px);border:0;\"></iframe></div>'; } }"
                        + "function inyectarPanelLicencias(){ var viewName=getViewName(); var hrefUpper=location.href.toUpperCase()+' '+viewName; var esLicencias = hrefUpper.includes('LICENCIAS') || hrefUpper.includes('LICENSES') || hrefUpper.includes('ESTADO%20PLAN') || hrefUpper.includes('ESTADO_PLAN'); var existing=document.getElementById('vexor-license-panel'); if(!esLicencias){ if(existing) existing.remove(); return; } if(existing) return; var target=document.querySelector('[data-testid=\"dashboard-view-container\"]') || document.querySelector('.dashboard-view') || document.body; var panel=document.createElement('div'); panel.id='vexor-license-panel'; panel.style.cssText='margin:12px;padding:16px;background:#fff;border-radius:12px;box-shadow:0 2px 10px rgba(0,0,0,0.1);border:1px solid #e6e6ef;font-family:sans-serif;'; panel.innerHTML=`<div style='text-align:center;margin-bottom:12px;'><div style='font-size:28px;'>📊</div><div style='font-weight:800;color:#8f6bc0;'>GESTIÓN DE LICENCIAS</div><div style='color:#85859c;font-size:11px;'>Administra tu app Vexor</div></div><div style='display:grid;grid-template-columns:1fr 1fr;gap:8px;'><button id='btn-estado' style='padding:12px 8px;border:none;border-radius:10px;background:linear-gradient(135deg,#8f6bc0,#3fb0ac);color:#fff;font-weight:700;font-size:12px;'>📊<br>ESTADO DE PLAN</button><button id='btn-activar' style='padding:12px 8px;border:none;border-radius:10px;background:#26263a;color:#fff;font-weight:700;font-size:12px;'>🔑<br>ACTIVAR PRO</button><button id='btn-comprar' style='padding:12px 8px;border-radius:10px;background:#fff;border:1px solid #e6e6ef;color:#26263a;font-weight:700;font-size:12px;'>💳<br>COMPRAR LICENCIA</button><button id='btn-acceso' style='padding:12px 8px;border-radius:10px;background:#fff;border:1px solid #e6e6ef;color:#26263a;font-weight:700;font-size:12px;'>⭐<br>CREAR ACCESO DIRECTO</button></div>`; if(target===document.body){ document.body.insertBefore(panel, document.body.firstChild); } else { target.insertBefore(panel, target.firstChild); } setTimeout(function(){ var b1=document.getElementById('btn-estado'); if(b1) b1.addEventListener('click', function(e){ e.preventDefault(); window.AndroidQR.estadoPlan(); }); var b2=document.getElementById('btn-activar'); if(b2) b2.addEventListener('click', function(e){ e.preventDefault(); window.AndroidQR.activarPro(); }); var b3=document.getElementById('btn-comprar'); if(b3) b3.addEventListener('click', function(e){ e.preventDefault(); window.AndroidQR.comprarLicencia(); }); var b4=document.getElementById('btn-acceso'); if(b4) b4.addEventListener('click', function(e){ e.preventDefault(); window.AndroidQR.crearAcceso(); }); }, 300);}"
                        + "var currentQrField = null;"
                        + "document.addEventListener('focusin',function(e){ var el=e.target; if(el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA') return; var label=getLabel(el); if(label.indexOf('QR')==-1){ if(currentQrField!==el){ try{window.AndroidQR.hideBtn();}catch(e){} currentQrField=null; } return; } currentQrField=el; if(!el.id) el.id='qr_'+Date.now(); try{window.AndroidQR.setId(el.id);}catch(e){} if(el.value.trim()==''){ try{window.AndroidQR.showBtn();}catch(e){} } else { try{window.AndroidQR.hideBtn();}catch(e){} } });"
                        + "document.addEventListener('focusout',function(e){ var el=e.target; if(el.tagName!=='INPUT'&&el.tagName!=='TEXTAREA') return; setTimeout(function(){ var active=document.activeElement; var stillQr=false; if(active){ var label=getLabel(active); if(label.indexOf('QR')!=-1) stillQr=true; } if(!stillQr){ try{window.AndroidQR.hideBtn();}catch(err){} currentQrField=null; } },200); });"
                        + "document.addEventListener('click', function(e){ setTimeout(function(){ var active=document.activeElement; if(!active || (active.tagName!=='INPUT' && active.tagName!=='TEXTAREA')){ try{window.AndroidQR.hideBtn();}catch(err){} currentQrField=null; } },200); }, true);"
                        + "document.addEventListener('click', function(e){ var el=e.target; for(var i=0;i<5 && el; i++){ if(el.innerText==='MENÚ' || (el.innerText||'').toUpperCase().indexOf('MENÚ')!=-1){ try{window.AndroidQR.cerrarPdf();}catch(err){} break; } if(el.getAttribute && el.getAttribute('aria-label') && el.getAttribute('aria-label').toLowerCase().indexOf('back')!=-1){ try{window.AndroidQR.cerrarPdf();}catch(err){} break; } el=el.parentElement; } }, true);"
                        + "window.addEventListener('popstate', function(){ try{window.AndroidQR.cerrarPdf();}catch(e){} });"
                        + "(function(){ var _push=history.pushState; history.pushState=function(){ try{window.AndroidQR.cerrarPdf();}catch(e){} return _push.apply(this, arguments); }; var _replace=history.replaceState; history.replaceState=function(){ try{window.AndroidQR.cerrarPdf();}catch(e){} return _replace.apply(this, arguments); }; })();"
                        + "function checkReinicio(){ }"
                        + "setInterval(function(){ embeber(); inyectarPanelLicencias(); },2000); embeber(); inyectarPanelLicencias();"
                        + "})()";
                view.evaluateJavascript(js,null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url){
                // 1. SIEMPRE PERMITIR LOGIN GOOGLE - NUNCA BLOQUEAR
                if(url.contains("accounts.google.com") || 
                   url.contains("accounts.youtube.com") ||
                   url.contains("google.com/signin") ||
                   url.contains("oauth") ||
                   url.contains("gstatic.com") ||
                   url.contains("googleusercontent.com") ||
                   url.contains("google.com/o/oauth") ||
                   url.contains("ServiceLogin") ||
                   url.contains("signin/v2") ||
                   url.contains("CheckCookie")){
                    return false;
                }
                
                // 2. Si aun no completo primer ingreso, no bloquear nada de appsheet
                if(!primerIngresoCompletado && url.contains("appsheet.com")){
                    if(pdfOverlay.getVisibility()==View.VISIBLE){ pdfOverlay.setVisibility(View.GONE); }
                    return false;
                }

                // 3. AHORA SI - PDF Y URL FUNCIONANDO DESPUES DEL LOGIN
                if(url.contains("gettablefileurl") || url.contains("getfile") || url.toLowerCase().contains(".pdf")){
                    if(!hasAccess()){ mostrarBloqueoPorExpiracion(); return true; }
                    descargarPdfDeAppSheet(url); return true;
                }
                // URLs externas -> visor PDF (despues del login)
                if(!url.contains("appsheet.com") && !url.contains("google.com") && !url.contains("gstatic.com") && !url.contains("googleusercontent.com")){
                    if(url.startsWith("http") &&!url.contains("datastudio.google.com") &&!url.contains("lookerstudio.google.com")){
                        if(!hasAccess()){ mostrarBloqueoPorExpiracion(); return true; }
                        mostrarLinkEnVisor(url); return true;
                    }
                }
                if(pdfOverlay.getVisibility()==View.VISIBLE){ pdfOverlay.setVisibility(View.GONE); }
                return false;
            }
        });

        webView.loadUrl(APPSHEET_URL);
    }

    private void initVexorLicensing(){
        SharedPreferences prefs = getSharedPreferences("VEXOR_PREFS", MODE_PRIVATE);
        deviceId = prefs.getString("vexor_device_id", "");
        if(deviceId.isEmpty()){
            deviceId = generarDeviceId();
            prefs.edit().putString("vexor_device_id", deviceId).apply();
        }
        isLicensed = prefs.getBoolean("vexor_licensed", false);
        licensePlan = prefs.getString("vexor_plan", "");
        licenseMax = prefs.getInt("vexor_max", 1);
        licenseUsed = prefs.getInt("vexor_used", 1);
        trialExpiresAt = prefs.getLong("vexor_trial_expires", 0);
        if(trialExpiresAt==0){
            trialExpiresAt = System.currentTimeMillis() + 30L*24*60*60*1000;
            prefs.edit().putLong("vexor_trial_expires", trialExpiresAt).putBoolean("vexor_trial_allowed", true).putBoolean("vexor_trial_active", true).apply();
        }
        recalcularTrial();
        new Thread(() -> syncTrialWithSheet()).start();
    }

    private String generarDeviceId(){
        try{
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            String raw = android.os.Build.MODEL + "||" + android.os.Build.MANUFACTURER + "||" + androidId + "||" + getPackageName();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for(byte b: hash){
                sb.append(String.format("%02x", b));
            }
            return "DEV-" + sb.toString().substring(0,16).toUpperCase();
        }catch(Exception e){
            return "DEV-" + System.currentTimeMillis();
        }
    }

    private void recalcularTrial(){
        SharedPreferences prefs = getSharedPreferences("VEXOR_PREFS", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long diff = trialExpiresAt - now;
        int days = (int)Math.ceil(diff / 86400000.0);
        if(days <=0){
            trialActive = false;
            trialAllowed = false;
            trialDaysLeft = 0;
            prefs.edit().putBoolean("vexor_trial_active", false).putBoolean("vexor_trial_allowed", false).putBoolean("vexor_trial_expired", true).putInt("vexor_days_left", 0).apply();
        }else{
            trialActive = true;
            trialAllowed = true;
            trialDaysLeft = days;
            prefs.edit().putBoolean("vexor_trial_active", true).putBoolean("vexor_trial_allowed", true).putInt("vexor_days_left", days).apply();
        }
    }

    private boolean hasAccess(){
        if(isLicensed) return true;
        return trialAllowed && trialActive;
    }

    private void syncTrialWithSheet(){
        try{
            String urlStr = GOOGLE_SHEET_API_URL + "?action=check_trial&device_id=" + deviceId;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line=br.readLine())!=null) sb.append(line);
            br.close();
            JSONObject data = new JSONObject(sb.toString());
            if(data.has("expires_at")){
                String expiresStr = data.optString("expires_at");
                long exp = 0;
                try{
                    exp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(expiresStr).getTime();
                }catch(Exception e){
                    try{
                        exp = new SimpleDateFormat("yyyy-MM-dd").parse(expiresStr).getTime();
                    }catch(Exception ee){}
                }
                if(exp>0){
                    trialExpiresAt = exp;
                    SharedPreferences prefs = getSharedPreferences("VEXOR_PREFS", MODE_PRIVATE);
                    prefs.edit().putLong("vexor_trial_expires", exp).putBoolean("vexor_trial_allowed", data.optBoolean("allowed", true)).putBoolean("vexor_trial_active", data.optBoolean("trial_active", true)).putBoolean("vexor_trial_expired", data.optBoolean("trial_expired", false)).putInt("vexor_days_left", data.optInt("days_left", trialDaysLeft)).apply();
                    trialAllowed = data.optBoolean("allowed", trialAllowed);
                    trialActive = data.optBoolean("trial_active", trialActive);
                    trialDaysLeft = data.optInt("days_left", trialDaysLeft);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void verificarLicenciaConSheet(String key, LicenseCallback cb){
        new Thread(() -> {
            try{
                String urlStr = GOOGLE_SHEET_API_URL + "?action=activate&license_key=" + Uri.encode(key) + "&device_id=" + deviceId;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line=br.readLine())!=null) sb.append(line);
                br.close();
                JSONObject data = new JSONObject(sb.toString());
                if(data.has("error")){
                    runOnUiThread(() -> cb.onResult(false, data.optString("error"), null));
                } else{
                    runOnUiThread(() -> cb.onResult(data.optBoolean("allowed", false), data.optString("message", ""), data));
                }
            }catch(Exception e){
                runOnUiThread(() -> cb.onResult(false, e.getMessage(), null));
            }
        }).start();
    }

    interface LicenseCallback{
        void onResult(boolean allowed, String msg, JSONObject data);
    }

    private void mostrarBloqueoPorExpiracion(){
        String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body{font-family:sans-serif;background:#f6f6fa;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center}.card{background:#fff;padding:24px;border-radius:12px;box-shadow:0 2px 10px rgba(0,0,0,0.1);max-width:320px}.btn{background:linear-gradient(135deg,#8f6bc0,#3fb0ac);color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;display:inline-block;font-weight:700;margin-top:12px}.btn2{margin-top:10px;background:#26263a;color:#fff;padding:10px 16px;border-radius:8px;border:none;font-weight:700}</style></head><body><div class='card'><h3>❌ Prueba terminada</h3><p>Tu prueba de 30 días terminó.<br><b>💳 Pago único, de por vida</b></p><a class='btn' href='"+PAYPAL_LINK+"' target='_blank'>💳 COMPRAR LICENCIA</a><br><br><button class='btn2' onclick=\"AndroidBridge.abrirActivar()\">🔑 ACTIVAR PRO</button></div></body></html>";
        pdfView.addJavascriptInterface(new Object(){ @JavascriptInterface public void abrirActivar(){ runOnUiThread(() -> { pdfOverlay.setVisibility(View.GONE); mostrarActivarProDialog(); }); } }, "AndroidBridge");
        pdfView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        findViewById(R.id.btnPdfMenu).setVisibility(View.VISIBLE);
        pdfOverlay.setVisibility(View.VISIBLE);
    }

    private void mostrarEstadoPlanDialog(){
        recalcularTrial();
        String estado;
        String detalle;
        if(isLicensed){
            estado = "✅ PRO ACTIVA - Dispositivos " + licenseUsed + "/" + licenseMax;
            detalle = "💳 Pago único - De por vida\nNo vuelves a pagar nunca más.\n\n📦 Plan: " + licensePlan + "\n📱 Dispositivos: " + licenseUsed + "/" + licenseMax + " (de por vida)\n\nTodo desbloqueado para siempre:\n✅ Data Studio embebido\n✅ PDFs y URLs\n✅ QR Scanner\n✅ Accesos directos ilimitados";
        }else if(trialActive && trialAllowed){
            estado = "⏳ Te quedan " + trialDaysLeft + " días";
            detalle = "⏳ Te quedan " + trialDaysLeft + " días de prueba gratis.\n📅 Expira: " + new SimpleDateFormat("dd/MM/yyyy").format(new Date(trialExpiresAt)) + "\n\n✅ Todo desbloqueado durante la prueba.\n💳 Al terminar compra licencia de por vida.\n💰 Pago único, sin mensualidades.";
        }else{
            estado = "❌ PRUEBA TERMINADA";
            detalle = "❌ Tu prueba de 30 días terminó.\n\nRestricciones activas:\n❌ Data Studio bloqueado\n❌ PDF bloqueado\n❌ URLs externas bloqueadas\n✅ QR Scanner sigue funcionando\n\n💳 Compra licencia PRO de por vida\n💰 Pago único, sin mensualidades.\n📱 Incluye 1, 3 o 5 dispositivos según tu plan.";
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40,40,40,40);
        TextView t1 = new TextView(this);
        t1.setText(estado);
        t1.setTextSize(16);
        t1.setTextColor(0xFF8f6bc0);
        t1.setPadding(0,0,0,12);
        TextView t2 = new TextView(this);
        t2.setText(detalle);
        t2.setTextSize(13);
        Button b1 = new Button(this);
        b1.setText("💳 COMPRAR LICENCIA");
        b1.setOnClickListener(v -> { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_LINK))); });
        Button b2 = new Button(this);
        b2.setText("🔑 ACTIVAR PRO");
        b2.setOnClickListener(v -> { mostrarActivarProDialog(); });
        layout.addView(t1);
        layout.addView(t2);
        layout.addView(b1);
        layout.addView(b2);
        new AlertDialog.Builder(this).setTitle("📊 ESTADO DE PLAN").setView(layout).setPositiveButton("Cerrar", null).show();
    }

    private void mostrarActivarProDialog(){
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40,40,40,40);
        TextView info = new TextView(this);
        if(isLicensed){
            info.setText("✅ PRO ACTIVA - Dispositivos " + licenseUsed + "/" + licenseMax + "\n💳 De por vida - Pago único\n\n🔑 Si quieres usar la misma clave en otro celular, pega la clave abajo:");
        } else if(trialActive){
            info.setText("⏳ PRUEBA - " + trialDaysLeft + " días restantes\n💳 Luego licencia de por vida\n💰 Pago único, sin mensualidades\n\n🔑 Pega tu clave VEXOR-... aquí abajo:");
        } else{
            info.setText("❌ VENCIDO\n💳 Compra licencia de por vida\n💰 Pago único, una sola vez\n\n🔑 Si ya pagaste, pega tu clave aquí abajo:");
        }
        info.setTextSize(12);
        info.setPadding(0,0,0,16);
        EditText inputKey = new EditText(this);
        inputKey.setHint("🔑 VEXOR-XXXX-XXXX");
        TextView error = new TextView(this);
        error.setTextColor(0xFFE0585A);
        error.setTextSize(11);
        TextView devicesInfo = new TextView(this);
        devicesInfo.setTextSize(11);
        devicesInfo.setTextColor(0xFF666666);
        if(isLicensed) devicesInfo.setText("✅ De por vida - " + licenseUsed + "/" + licenseMax + " dispositivos");
        else devicesInfo.setText("💳 Licencia de por vida - Pago único - Sin mensualidades");
        Button btnComprar = new Button(this);
        btnComprar.setText("💳 COMPRAR LICENCIA");
        Button btnActivar = new Button(this);
        btnActivar.setText("🔑 ACTIVAR PRO");
        layout.addView(info);
        layout.addView(inputKey);
        layout.addView(error);
        layout.addView(devicesInfo);
        layout.addView(btnActivar);
        layout.addView(btnComprar);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("🔑 ACTIVAR PRO").setView(layout).setNegativeButton("Cerrar", null).create();
        dialog.show();
        btnComprar.setOnClickListener(v -> { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PAYPAL_LINK))); });
        btnActivar.setOnClickListener(v -> {
            String k = inputKey.getText().toString().trim().toUpperCase();
            if(k.isEmpty()){
                error.setText("⚠ Ingresa tu clave");
                return;
            }
            btnActivar.setEnabled(false);
            btnActivar.setText("⏳ Verificando...");
            verificarLicenciaConSheet(k, (allowed, msg, data) -> {
                btnActivar.setEnabled(true);
                btnActivar.setText("🔑 ACTIVAR PRO");
                if(allowed){
                    SharedPreferences prefs = getSharedPreferences("VEXOR_PREFS", MODE_PRIVATE);
                    prefs.edit().putBoolean("vexor_licensed", true).putString("vexor_plan", data.optString("plan", "PRO")).putString("vexor_license_key", k).putInt("vexor_max", data.optInt("max", 1)).putInt("vexor_used", data.optInt("used", 1)).apply();
                    isLicensed = true;
                    licensePlan = data.optString("plan", "PRO");
                    licenseMax = data.optInt("max", 1);
                    licenseUsed = data.optInt("used", 1);
                    Toast.makeText(this, "✅ PRO de por vida - " + licenseUsed + "/" + licenseMax, Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    webView.reload();
                }else{
                    error.setText("❌ " + (msg!=null &&!msg.isEmpty()? msg : "Clave inválida"));
                }
            });
        });
    }

    private void mostrarDialogCrearAccesoDirecto(){
        selectedIconBitmap = null;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40,40,40,40);
        previewIconView = new ImageView(this);
        previewIconView.setLayoutParams(new LinearLayout.LayoutParams(200,200));
        ((LinearLayout.LayoutParams)previewIconView.getLayoutParams()).gravity = 17;
        previewIconView.setImageResource(android.R.drawable.ic_menu_gallery);
        previewIconView.setBackgroundResource(android.R.drawable.picture_frame);
        layout.addView(previewIconView);
        Button btnSubir = new Button(this);
        btnSubir.setText("📁 SUBIR ICONO");
        layout.addView(btnSubir);
        EditText inputNombre = new EditText(this);
        inputNombre.setHint("Nombre del acceso directo");
        inputNombre.setText("Mi App");
        layout.addView(inputNombre);
        TextView desc = new TextView(this);
        desc.setText("\n512x512 PNG recomendado");
        desc.setTextSize(12);
        desc.setTextColor(0xFF777777);
        layout.addView(desc);
        btnSubir.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Selecciona icono"), 1004);
        });
        new AlertDialog.Builder(this).setTitle("Crear Acceso Directo").setView(layout).setPositiveButton("CREAR", (d,w) -> {
            String nombre = inputNombre.getText().toString().trim();
            if(nombre.isEmpty()) nombre = "Mi App";
            if(selectedIconBitmap == null){
                Toast.makeText(this, "Sube un icono primero", Toast.LENGTH_SHORT).show();
                return;
            }
            crearAccesoDirectoLimpio(nombre, selectedIconBitmap, APPSHEET_URL);
        }).setNegativeButton("Cancelar", null).show();
    }

    private void crearAccesoDirectoLimpio(String nombre, Bitmap icono, String url){
        try{
            Bitmap base = Bitmap.createScaledBitmap(icono, 420, 420, true);
            Bitmap iconoConMargen = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(iconoConMargen);
            canvas.drawBitmap(base, (512-420)/2f, (512-420)/2f, null);
            Icon iconFinal = Icon.createWithAdaptiveBitmap(iconoConMargen);
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            Intent shortcutIntent = new Intent(this, MainActivity.class);
            shortcutIntent.setAction(Intent.ACTION_MAIN);
            shortcutIntent.putExtra("direct_url", url);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ShortcutInfo info = new ShortcutInfo.Builder(this, "vexor_" + System.currentTimeMillis()).setShortLabel(nombre).setLongLabel(nombre).setIcon(iconFinal).setIntent(shortcutIntent).build();
            sm.requestPinShortcut(info, null);
            Toast.makeText(this, "Listo", Toast.LENGTH_LONG).show();
        }catch(Exception e){
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void grabarAudioInterno(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 102);
            if(filePathCallback!=null){
                filePathCallback.onReceiveValue(null);
                filePathCallback=null;
            }
            return;
        }
        try{
            tempAudioFile = new File(getCacheDir(), "AUD_"+new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())+".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(tempAudioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            AlertDialog d = new AlertDialog.Builder(this).setTitle("🎤 Grabando...").setMessage("Habla ahora").setPositiveButton("DETENER", (dialog, w) -> { detenerAudio(); }).setCancelable(false).show();
            webView.postDelayed(() -> {
                if(recorder!=null) {
                    d.dismiss();
                    detenerAudio();
                }
            }, 60000);
        }catch(Exception e){
            Toast.makeText(this, "Error: "+e.getMessage(), Toast.LENGTH_LONG).show();
            if(filePathCallback!=null){
                filePathCallback.onReceiveValue(null);
                filePathCallback=null;
            }
        }
    }

    private void detenerAudio(){
        try{
            if(recorder!=null){
                recorder.stop();
                recorder.release();
                recorder=null;
            }
            if(tempAudioFile!=null && tempAudioFile.exists()){
                Uri uri = FileProvider.getUriForFile(this, getPackageName()+".fileprovider", tempAudioFile);
                if(filePathCallback!=null){
                    filePathCallback.onReceiveValue(new Uri[]{uri});
                    filePathCallback=null;
                }
            }else{
                if(filePathCallback!=null){
                    filePathCallback.onReceiveValue(null);
                    filePathCallback=null;
                }
            }
        }catch(Exception e){
            if(filePathCallback!=null){
                filePathCallback.onReceiveValue(null);
                filePathCallback=null;
            }
        }
    }

    private void descargarPdfDeAppSheet(String urlPdf){
        Toast.makeText(this, "Abriendo PDF...", Toast.LENGTH_SHORT).show();
        String cookie = CookieManager.getInstance().getCookie(urlPdf);
        String userAgent = webView.getSettings().getUserAgentString();
        new Thread(() -> {
            try{
                URL url = new URL(urlPdf);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if(cookie!=null) conn.setRequestProperty("Cookie", cookie);
                conn.setRequestProperty("User-Agent", userAgent);
                conn.connect();
                InputStream is = conn.getInputStream();
                String fileName = "GUIA_"+new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())+".pdf";
                File file = new File(getCacheDir(), fileName);
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[4096];
                int len;
                while((len=is.read(buffer))!=-1){
                    fos.write(buffer,0,len);
                }
                fos.close();
                is.close();
                runOnUiThread(() -> mostrarPdfEnVisor(file));
            }catch(Exception e){
                runOnUiThread(() -> Toast.makeText(this, "Error PDF: "+e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void mostrarPdfEnVisor(File file){
        try{
            pdfFileActual = file;
            findViewById(R.id.btnPdfMenu).setVisibility(View.VISIBLE);
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while((r = fis.read(buf))!=-1) bos.write(buf, 0, r);
            fis.close();
            String base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><script src='https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js'></script><style>body{margin:0;background:#525659;padding:0}.page{margin:10px auto;display:block;box-shadow:0 0 10px #000;width:100%}</style></head><body><div id='container'></div><script>var pdfData=atob('"+base64+"'); pdfjsLib.getDocument({data: pdfData}).promise.then(function(pdf){ var container=document.getElementById('container'); for(let i=1;i<=pdf.numPages;i++){ let canvas=document.createElement('canvas'); canvas.className='page'; container.appendChild(canvas); pdf.getPage(i).then(function(page){ var viewport=page.getViewport({scale:1.2}); canvas.height=viewport.height; canvas.width=viewport.width; page.render({canvasContext:canvas.getContext('2d'), viewport:viewport}); }); } });</script></body></html>";
            pdfView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            pdfOverlay.setVisibility(View.VISIBLE);
        }catch(Exception e){
            Toast.makeText(this, "Error visor: "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarLinkEnVisor(String url){
        pdfFileActual = null;
        findViewById(R.id.btnPdfMenu).setVisibility(View.GONE);
        pdfView.loadUrl(url);
        pdfOverlay.setVisibility(View.VISIBLE);
    }

    private void copiarADescargas(File src){
        try{
            File dst = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), src.getName());
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[4096];
            int len;
            while((len = in.read(buf)) > 0) out.write(buf, 0, len);
            in.close();
            out.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void abrirScanner(){
        IntentIntegrator i=new IntentIntegrator(this);
        i.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        i.setPrompt("Escanea el QR");
        i.setBeepEnabled(true);
        i.setOrientationLocked(false);
        i.initiateScan();
    }

    @Override public void onBackPressed() {
        if(recorder!=null){
            detenerAudio();
            return;
        }
        if(pdfOverlay.getVisibility()==View.VISIBLE){
            pdfOverlay.setVisibility(View.GONE);
            pdfView.loadUrl("about:blank");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 600) {
            webView.clearHistory();
            webView.loadUrl(APPSHEET_URL);
            finishAffinity();
            return;
        }
        lastBackPress = now;
        if (webView.canGoBack()){
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(r!=null&&r.getContents()!=null){
            String qr=r.getContents();
            Toast.makeText(this,"QR: "+qr,Toast.LENGTH_SHORT).show();
            String esc=qr.replace("\\","\\\\").replace("'","\\'").replace("\"","\\\"").replace("\n","\\n");
            String id = lastQrId!=null? lastQrId : "";
            String inject = "javascript:(function(){ var v='"+esc+"'; var el=document.getElementById('"+id+"'); if(!el) el=document.activeElement; if(!el) return; el.focus(); var lastValue = el.value; el.value = v; var tracker = el._valueTracker; if(tracker){ tracker.setValue(lastValue); } el.dispatchEvent(new Event('input', {bubbles:true})); el.dispatchEvent(new Event('change', {bubbles:true})); })()";
            webView.evaluateJavascript(inject, null);
            webView.postDelayed(() -> btnQr.setVisibility(View.GONE), 600);
            return;
        }
        if(requestCode==1001&&filePathCallback!=null){
            Uri[] res=null;
            if(resultCode==RESULT_OK && data!=null && data.getData()!=null) res=new Uri[]{data.getData()};
            filePathCallback.onReceiveValue(res);
            filePathCallback=null;
        }
        if(requestCode==1003&&filePathCallback!=null){
            Uri[] res=null;
            if(resultCode==RESULT_OK && cameraImageUri!=null) res=new Uri[]{cameraImageUri};
            filePathCallback.onReceiveValue(res);
            filePathCallback=null;
        }
        if(requestCode==1004 && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            try{
                Uri uri = data.getData();
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                selectedIconBitmap = bmp;
                if(previewIconView!= null) previewIconView.setImageBitmap(bmp);
            }catch(Exception e){
                Toast.makeText(this, "Error icono: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
