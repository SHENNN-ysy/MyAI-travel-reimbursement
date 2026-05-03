package com.aidemo.myaitravelreimbursement.service;

import com.aidemo.myaitravelreimbursement.dto.request.AiRecognizeDTO;
import com.aidemo.myaitravelreimbursement.dto.response.RecognitionResultVO;

import java.io.File;

/**
 * AI 识别服务接口
 */
public interface AiRecognitionService {

    RecognitionResultVO recognize(Long fileId, String type);

    RecognitionResultVO recognizeFromFile(File file, String type);
}
