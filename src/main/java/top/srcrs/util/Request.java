package top.srcrs.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.srcrs.domain.Cookie;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 封装的网络请求工具类
 *
 * @author srcrs
 * @Time 2020-10-31
 */
public class Request {
    private static final Logger LOGGER = LoggerFactory.getLogger(Request.class);
    private static final Cookie COOKIE = Cookie.getInstance();

    /**
     * 使用连接池管理 HttpClient 资源，防止连接泄露
     */
    private static final CloseableHttpClient CLIENT;

    static {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(50);
        cm.setDefaultMaxPerRoute(20);
        RequestConfig defaultConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .setConnectTimeout(8000)
                .setSocketTimeout(10000)
                .setConnectionRequestTimeout(5000)
                .build();
        CLIENT = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(defaultConfig)
                .build();
    }

    private Request() {}

    /**
     * 发送 GET 请求
     *
     * @param url 请求的完整 URL
     * @return JSONObject 解析后的 JSON 对象，失败时返回空 JSONObject
     */
    public static JSONObject get(String url) {
        return executeWithRetry(new HttpGet(url), null, 3);
    }

    /**
     * 发送 POST 请求（表单格式）
     *
     * @param url  请求的完整 URL
     * @param body 表单请求体字符串
     * @return JSONObject 解析后的 JSON 对象，失败时返回空 JSONObject
     */
    public static JSONObject post(String url, String body) {
        HttpPost httpPost = new HttpPost(url);
        if (body != null) {
            httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED.withCharset(StandardCharsets.UTF_8)));
        }
        return executeWithRetry(httpPost, null, 3);
    }

    /**
     * 发送 POST 请求（JSON 格式）
     *
     * @param url      请求的完整 URL
     * @param jsonBody JSON 请求体字符串
     * @return JSONObject 解析后的 JSON 对象
     */
    public static JSONObject postJson(String url, String jsonBody) {
        HttpPost httpPost = new HttpPost(url);
        if (jsonBody != null) {
            httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
        }
        return executeWithRetry(httpPost, ContentType.APPLICATION_JSON.getMimeType(), 3);
    }

    /**
     * 带重试机制的 HTTP 执行器
     *
     * @param request     HTTP 请求对象
     * @param contentType 内容类型
     * @param maxRetries  最大尝试次数
     * @return JSONObject 解析后的 JSON 对象
     */
    private static JSONObject executeWithRetry(HttpRequestBase request, String contentType, int maxRetries) {
        setHeader(request, contentType);
        String url = request.getURI().toString();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (CloseableHttpResponse response = CLIENT.execute(request)) {
                HttpEntity entity = response.getEntity();
                String respContent = entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";
                if (respContent.trim().isEmpty()) {
                    return new JSONObject();
                }
                return JSON.parseObject(respContent);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    LOGGER.error("HTTP 请求失败(已达最大重试次数) -- URL: {}, 错误: {}", url, e.getMessage(), e);
                    return new JSONObject();
                }
                LOGGER.warn("HTTP 请求异常，第 {} 次重试 -- URL: {}, 原因: {}", attempt, url, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new JSONObject();
                }
            }
        }
        return new JSONObject();
    }

    /**
     * 统一配置请求头，避免向第三方服务泄漏百度 Cookie 或添加错误的 Host 头
     *
     * @param request     HTTP 请求对象
     * @param contentType 显式指定的内容类型
     */
    private static void setHeader(HttpRequestBase request, String contentType) {
        request.addHeader("Connection", "keep-alive");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        if (contentType != null) {
            request.addHeader("Content-Type", contentType);
        } else if (request instanceof HttpPost && request.getFirstHeader("Content-Type") == null) {
            request.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        }

        // 仅对百度系域名注入 Cookie，防止第三方推送服务收到无关 Cookie 或发生 Host 冲突
        URI uri = request.getURI();
        if (uri != null && uri.getHost() != null && uri.getHost().contains("baidu.com")) {
            String bdussCookie = COOKIE.getCookie();
            if (bdussCookie != null && !bdussCookie.trim().isEmpty()) {
                request.addHeader("Cookie", bdussCookie);
            }
        }
    }
}
