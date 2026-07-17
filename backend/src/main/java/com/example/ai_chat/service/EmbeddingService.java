package com.example.ai_chat.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    @Value("${llm.api_key}")
    private String apiKey;

    private static final String EMBEDDING_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    private static final String MODEL = "text-embedding-v4";

    /** 将单段文本转为向量 */
    public float[] embed(String text) {
        float[][] batch = embedBatch(new String[]{text});
        return batch.length > 0 ? batch[0] : new float[0];
    }

    /** 批量文本转向量 */
    public float[][] embedBatch(String[] texts) {
        JSONObject body = new JSONObject();
        body.set("model", MODEL);
        body.set("input", JSONUtil.parseArray(texts));

        HttpResponse resp = HttpRequest.post(EMBEDDING_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(60000)
                .execute();

        if (!resp.isOk()) {
            throw new RuntimeException("Embedding API 调用失败: " + resp.body());
        }

        JSONObject result = JSONUtil.parseObj(resp.body());
        JSONArray dataArr = result.getJSONArray("data");
        float[][] embeddings = new float[dataArr.size()][];
        for (int i = 0; i < dataArr.size(); i++) {
            JSONObject item = dataArr.getJSONObject(i);
            JSONArray embArr = item.getJSONArray("embedding");
            float[] vec = new float[embArr.size()];
            for (int j = 0; j < embArr.size(); j++) {
                vec[j] = embArr.getFloat(j).floatValue();
            }
            embeddings[i] = vec;
        }
        return embeddings;
    }

    /** 余弦相似度 */
    public static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (float)(Math.sqrt(normA) * Math.sqrt(normB));
    }
}
