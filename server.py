from flask import Flask, request, jsonify
from flask_cors import CORS
import cv2
import numpy as np
import base64
from models import SCRFD, Attribute
from utils.helpers import Face, draw_face_info
import warnings
import logging

# Configure logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

warnings.filterwarnings("ignore")

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes

# Load models
detection_model = None
attribute_model = None

def load_models():
    global detection_model, attribute_model
    try:
        logger.info("Loading face detection models...")
        detection_model = SCRFD(model_path="weights/det_10g.onnx")
        attribute_model = Attribute(model_path="weights/genderage.onnx")
        logger.info("Models loaded successfully")
    except Exception as e:
        logger.error(f"Error loading models: {e}")
        raise

@app.route('/health')
def health_check():
    logger.info("Health check endpoint called")
    try:
        return jsonify({
            "status": "ok",
            "message": "Server is running",
            "models_loaded": detection_model is not None and attribute_model is not None
        })
    except Exception as e:
        logger.error(f"Error in health check: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/analyze', methods=['POST'])
def analyze_image():
    logger.info("Analyze endpoint called")
    try:
        # Get image data from request
        data = request.get_json()
        if not data or 'image' not in data:
            logger.error("No image data provided in request")
            return jsonify({"status": "error", "message": "No image data provided"}), 400

        logger.info("Decoding base64 image...")
        # Decode base64 image
        image_data = base64.b64decode(data['image'])
        nparr = np.frombuffer(image_data, np.uint8)
        frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if frame is None:
            logger.error("Failed to decode image")
            return jsonify({"status": "error", "message": "Failed to decode image"}), 400

        logger.info(f"Image decoded successfully, size: {frame.shape}")
        
        # Detect faces
        logger.info("Detecting faces...")
        boxes_list, points_list = detection_model.detect(frame)
        logger.info(f"Found {len(boxes_list)} faces")
        
        faces_info = []
        for boxes, keypoints in zip(boxes_list, points_list):
            *bbox, conf_score = boxes
            gender, age = attribute_model.get(frame, bbox)
            face = Face(kps=keypoints, bbox=bbox, age=age, gender=gender)
            draw_face_info(frame, face)
            
            faces_info.append({
                "age": int(age),
                "gender": str(gender),
                "confidence": float(conf_score)
            })

        # Convert processed image back to base64
        logger.info("Converting processed image to base64...")
        _, buffer = cv2.imencode('.jpg', frame)
        processed_image = base64.b64encode(buffer).decode('utf-8')

        logger.info("Sending response...")
        return jsonify({
            "status": "success",
            "message": "Image processed successfully",
            "faces_count": len(faces_info),
            "faces": faces_info,
            "processed_image": processed_image
        })

    except Exception as e:
        logger.error(f"Error processing image: {e}", exc_info=True)
        return jsonify({"status": "error", "message": str(e)}), 500

if __name__ == "__main__":
    try:
        logger.info("Starting server initialization...")
        load_models()
        logger.info("Starting Flask server...")
        app.run(host='0.0.0.0', port=5000, debug=True)
    except Exception as e:
        logger.error(f"Server startup failed: {e}", exc_info=True) 