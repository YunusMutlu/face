from flask import Flask, request, jsonify
from flask_cors import CORS
import cv2
import numpy as np
import base64
import logging
from models import SCRFD, Attribute
from utils.helpers import Face, draw_face_info

# Loglama ayarları
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # CORS desteği ekle

# Model yolları
DETECTION_MODEL_PATH = "weights/det_10g.onnx"
ATTRIBUTE_MODEL_PATH = "weights/genderage.onnx"

# Modelleri yükle
try:
    logger.info("Modeller yükleniyor...")
    detection_model = SCRFD(model_path=DETECTION_MODEL_PATH)
    attribute_model = Attribute(model_path=ATTRIBUTE_MODEL_PATH)
    logger.info("Modeller başarıyla yüklendi")
except Exception as e:
    logger.error(f"Model yükleme hatası: {str(e)}")
    raise

@app.route('/analyze', methods=['POST'])
def analyze_image():
    try:
        logger.info("Yeni analiz isteği alındı")
        
        # Base64 formatındaki görüntüyü al
        data = request.get_json()
        if not data or 'image' not in data:
            logger.error("Görüntü verisi bulunamadı")
            return jsonify({'error': 'No image data provided'}), 400

        logger.info("Görüntü verisi alındı, base64 decode ediliyor...")
        # Base64'ü decode et
        try:
            image_data = base64.b64decode(data['image'])
            nparr = np.frombuffer(image_data, np.uint8)
            frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        except Exception as e:
            logger.error(f"Base64 decode hatası: {str(e)}")
            return jsonify({'error': 'Invalid base64 image data'}), 400

        if frame is None:
            logger.error("Görüntü decode edilemedi")
            return jsonify({'error': 'Invalid image data'}), 400

        logger.info(f"Görüntü başarıyla decode edildi, boyut: {frame.shape}")
        
        # Yüz tespiti ve öznitelik analizi
        logger.info("Yüz tespiti başlatılıyor...")
        try:
            boxes_list, points_list = detection_model.detect(frame)
            logger.info(f"Tespit edilen yüz sayısı: {len(boxes_list)}")
        except Exception as e:
            logger.error(f"Yüz tespiti hatası: {str(e)}")
            return jsonify({'error': 'Face detection failed'}), 500
        
        faces_info = []

        for i, (boxes, keypoints) in enumerate(zip(boxes_list, points_list)):
            try:
                logger.info(f"Yüz {i+1} analiz ediliyor...")
                *bbox, conf_score = boxes
                gender, age = attribute_model.get(frame, bbox)
                face = Face(kps=keypoints, bbox=bbox, age=age, gender=gender)
                draw_face_info(frame, face)
                
                faces_info.append({
                    'age': age,
                    'gender': gender,
                    'confidence': float(conf_score)
                })
                logger.info(f"Yüz {i+1} analizi tamamlandı - Yaş: {age}, Cinsiyet: {gender}")
            except Exception as e:
                logger.error(f"Yüz {i+1} analizi hatası: {str(e)}")
                continue

        # İşlenmiş görüntüyü base64'e çevir
        logger.info("İşlenmiş görüntü base64'e dönüştürülüyor...")
        _, buffer = cv2.imencode('.jpg', frame)
        processed_image_base64 = base64.b64encode(buffer).decode('utf-8')
        logger.info("İşlenmiş görüntü başarıyla dönüştürüldü")

        # Yanıt hazırla
        response = {
            'status': 'success',
            'message': 'Görüntü başarıyla işlendi',
            'faces_count': len(faces_info),
            'faces': faces_info,
            'processed_image': processed_image_base64  # İşlenmiş görüntüyü de gönder
        }
        
        logger.info("Yanıt hazırlandı")
        return jsonify(response)

    except Exception as e:
        logger.error(f"Beklenmeyen hata: {str(e)}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/test', methods=['GET'])
def test():
    return jsonify({'status': 'success', 'message': 'API is working'})

if __name__ == '__main__':
    logger.info("Flask uygulaması başlatılıyor...")
    app.run(host='0.0.0.0', port=5000, debug=True) 