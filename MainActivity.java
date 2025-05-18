package com.proje.facedetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import com.android.volley.DefaultRetryPolicy;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ImageView imageView;
    private Button analyzeButton;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String API_URL = "http://192.168.1.105:5000/analyze"; // Bilgisayarınızın IP adresi
    // private static final String API_URL = "http://10.0.2.2:5000/analyze"; // Android Emulator için
    private RequestQueue requestQueue;
    private boolean isProcessing = false;
    private ProgressBar progressBar;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.d(TAG, "Kamera sonucu alındı");
                    Bundle extras = result.getData().getExtras();
                    if (extras != null) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data");
                        if (imageBitmap != null) {
                            Log.d(TAG, "Fotoğraf başarıyla alındı, boyut: " + imageBitmap.getWidth() + "x" + imageBitmap.getHeight());
                            imageView.setImageBitmap(imageBitmap);
                            analyzeImage(imageBitmap);
                        } else {
                            Log.e(TAG, "Fotoğraf null geldi");
                            Toast.makeText(this, "Fotoğraf alınamadı", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Extras null geldi");
                        Toast.makeText(this, "Fotoğraf verisi alınamadı", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d(TAG, "Kamera iptal edildi veya hata oluştu");
                    Toast.makeText(this, "Fotoğraf çekilemedi", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        analyzeButton = findViewById(R.id.analyzeButton);
        progressBar = findViewById(R.id.progressBar);
        requestQueue = Volley.newRequestQueue(this);

        // Test server connection on startup
        testServerConnection();

        analyzeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isProcessing) {
                    if (checkCameraPermission()) {
                        takePicture();
                    } else {
                        requestCameraPermission();
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        isProcessing = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (requestQueue != null) {
            requestQueue.cancelAll(this);
        }
    }

    private boolean checkCameraPermission() {
        boolean hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Kamera izni kontrolü: " + hasPermission);
        return hasPermission;
    }

    private void requestCameraPermission() {
        Log.d(TAG, "Kamera izni isteniyor");
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Kamera izni verildi");
                takePicture();
            } else {
                Log.d(TAG, "Kamera izni reddedildi");
                Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show();
                Toast.makeText(this, "Lütfen ayarlardan kamera iznini etkinleştirin", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void takePicture() {
        Log.d(TAG, "Fotoğraf çekme işlemi başlatılıyor");
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        } else {
            Log.e(TAG, "Kamera uygulaması bulunamadı");
            Toast.makeText(this, "Kamera uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void testServerConnection() {
        Log.d(TAG, "Testing server connection to: " + API_URL.replace("/analyze", "/health"));
        JsonObjectRequest testRequest = new JsonObjectRequest(
                Request.Method.GET,
                API_URL.replace("/analyze", "/health"),
                null,
                response -> {
                    try {
                        String status = response.getString("status");
                        String message = response.getString("message");
                        boolean modelsLoaded = response.getBoolean("models_loaded");
                        Log.d(TAG, "Server connection test successful");
                        Log.d(TAG, "Status: " + status);
                        Log.d(TAG, "Message: " + message);
                        Log.d(TAG, "Models loaded: " + modelsLoaded);
                        
                        if (!modelsLoaded) {
                            Toast.makeText(this, 
                                "Sunucu modelleri yükleyemedi. Lütfen sunucu loglarını kontrol edin.", 
                                Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing server response: " + e.getMessage());
                        Toast.makeText(this, 
                            "Sunucu yanıtı işlenemedi: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    Log.e(TAG, "Server connection test failed: " + error.getMessage());
                    if (error.networkResponse != null) {
                        Log.e(TAG, "Error code: " + error.networkResponse.statusCode);
                        Log.e(TAG, "Error data: " + new String(error.networkResponse.data));
                    }
                    
                    if (error instanceof com.android.volley.NoConnectionError) {
                        Log.e(TAG, "No connection error - server might be down or unreachable");
                        Toast.makeText(this, 
                            "Sunucu bağlantısı kurulamadı. Lütfen:\n" +
                            "1. Python sunucunuzun çalıştığından emin olun\n" +
                            "2. Sunucunun doğru portta (5000) çalıştığından emin olun\n" +
                            "3. Sunucunun tüm arayüzleri dinlediğinden emin olun (host='0.0.0.0')\n" +
                            "4. Aynı ağda olduğunuzdan emin olun\n" +
                            "5. Güvenlik duvarı ayarlarınızı kontrol edin\n" +
                            "6. IP adresinin doğru olduğundan emin olun: " + API_URL, 
                            Toast.LENGTH_LONG).show();
                    } else if (error instanceof com.android.volley.TimeoutError) {
                        Log.e(TAG, "Timeout error - server took too long to respond");
                        Toast.makeText(this, 
                            "Sunucu yanıt vermedi. Lütfen:\n" +
                            "1. Python sunucunuzun çalıştığından emin olun\n" +
                            "2. Sunucunun yanıt süresini kontrol edin\n" +
                            "3. Ağ bağlantınızı kontrol edin", 
                            Toast.LENGTH_LONG).show();
                    } else {
                        Log.e(TAG, "Unknown error type: " + error.getClass().getName());
                        Toast.makeText(this, 
                            "Bağlantı hatası: " + error.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                return headers;
            }
        };

        // Set a shorter timeout for the health check
        testRequest.setRetryPolicy(new DefaultRetryPolicy(
                5000, // 5 seconds timeout
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(testRequest);
        Log.d(TAG, "Health check request added to queue");
    }

    private void analyzeImage(Bitmap bitmap) {
        if (isProcessing) return;
        isProcessing = true;

        try {
            // Show progress bar and disable button
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
            analyzeButton.setEnabled(false);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            String encodedImage = Base64.getEncoder().encodeToString(byteArray);
            Log.d(TAG, "Görüntü base64'e dönüştürüldü, uzunluk: " + encodedImage.length());

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("image", encodedImage);
            Log.d(TAG, "JSON oluşturuldu, API isteği gönderiliyor: " + API_URL);

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                    Request.Method.POST,
                    API_URL,
                    jsonObject,
                    response -> {
                        // Hide progress bar and enable button
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        analyzeButton.setEnabled(true);
                        isProcessing = false;
                        
                        try {
                            String status = response.getString("status");
                            String message = response.getString("message");
                            int facesCount = response.getInt("faces_count");
                            
                            Log.d(TAG, "Status: " + status);
                            Log.d(TAG, "Message: " + message);
                            Log.d(TAG, "Faces Count: " + facesCount);

                            // İşlenmiş görüntüyü al ve göster
                            if (response.has("processed_image")) {
                                String processedImageBase64 = response.getString("processed_image");
                                Log.d(TAG, "İşlenmiş görüntü base64 alındı, uzunluk: " + processedImageBase64.length());
                                
                                try {
                                    byte[] imageBytes = Base64.getDecoder().decode(processedImageBase64);
                                    Log.d(TAG, "Base64 decode edildi, byte array uzunluğu: " + imageBytes.length);
                                    
                                    Bitmap processedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                    if (processedBitmap != null) {
                                        Log.d(TAG, "Bitmap oluşturuldu, boyut: " + processedBitmap.getWidth() + "x" + processedBitmap.getHeight());
                                        runOnUiThread(() -> {
                                            imageView.setImageBitmap(processedBitmap);
                                            Log.d(TAG, "İşlenmiş görüntü ImageView'e set edildi");
                                        });
                                    } else {
                                        Log.e(TAG, "Bitmap oluşturulamadı");
                                        Toast.makeText(MainActivity.this, "İşlenmiş görüntü oluşturulamadı", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (IllegalArgumentException e) {
                                    Log.e(TAG, "Base64 decode hatası: " + e.getMessage());
                                    Toast.makeText(MainActivity.this, "İşlenmiş görüntü decode edilemedi", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Log.e(TAG, "API yanıtında processed_image yok");
                                Toast.makeText(MainActivity.this, "İşlenmiş görüntü alınamadı", Toast.LENGTH_SHORT).show();
                            }

                            if (response.has("faces")) {
                                JSONArray faces = response.getJSONArray("faces");
                                StringBuilder faceInfo = new StringBuilder();
                                for (int i = 0; i < faces.length(); i++) {
                                    JSONObject face = faces.getJSONObject(i);
                                    faceInfo.append("Yaş: ").append(face.getInt("age")).append("\n");
                                    faceInfo.append("Cinsiyet: ").append(face.getString("gender")).append("\n");
                                    faceInfo.append("Güven: ").append(face.getDouble("confidence")).append("\n\n");
                                }
                                String info = faceInfo.toString();
                                Log.d(TAG, "Yüz bilgileri: " + info);
                                Toast.makeText(MainActivity.this, info, Toast.LENGTH_LONG).show();
                            } else {
                                Log.e(TAG, "API yanıtında faces yok");
                                Toast.makeText(MainActivity.this, "Yüz bilgileri alınamadı", Toast.LENGTH_SHORT).show();
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "JSON işleme hatası: " + e.getMessage());
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "JSON işleme hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    },
                    error -> {
                        // Hide progress bar and enable button
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        analyzeButton.setEnabled(true);
                        isProcessing = false;

                        Log.e(TAG, "API hatası: " + error.getMessage());
                        if (error.networkResponse != null) {
                            Log.e(TAG, "Hata kodu: " + error.networkResponse.statusCode);
                            Log.e(TAG, "Hata verisi: " + new String(error.networkResponse.data));
                        }
                        error.printStackTrace();
                        
                        // Bağlantı hatası kontrolü
                        if (error instanceof com.android.volley.NoConnectionError) {
                            Log.e(TAG, "Bağlantı hatası: Sunucuya ulaşılamıyor");
                            Toast.makeText(MainActivity.this, 
                                "Sunucuya bağlanılamıyor. Lütfen:\n" +
                                "1. Python sunucunuzun çalıştığından emin olun\n" +
                                "2. Sunucunun doğru portta (5000) çalıştığından emin olun\n" +
                                "3. Sunucunun tüm arayüzleri dinlediğinden emin olun (host='0.0.0.0')\n" +
                                "4. Aynı ağda olduğunuzdan emin olun", 
                                Toast.LENGTH_LONG).show();
                        } else if (error instanceof com.android.volley.TimeoutError) {
                            Log.e(TAG, "Zaman aşımı hatası");
                            Toast.makeText(MainActivity.this, 
                                "Sunucu yanıt vermedi. Lütfen:\n" +
                                "1. Python sunucunuzun çalıştığından emin olun\n" +
                                "2. Sunucunun yanıt süresini kontrol edin", 
                                Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Hata: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Accept", "application/json");
                    return headers;
                }
            };

            // Increase timeout duration
            jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                    30000, // 30 seconds timeout
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            ));

            requestQueue.add(jsonObjectRequest);
            Log.d(TAG, "API isteği kuyruğa eklendi");
        } catch (JSONException e) {
            // Hide progress bar and enable button
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            analyzeButton.setEnabled(true);
            isProcessing = false;

            Log.e(TAG, "JSON oluşturma hatası: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "JSON oluşturma hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
} 