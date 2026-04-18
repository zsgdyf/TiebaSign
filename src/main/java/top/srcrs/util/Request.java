package top.srcrs.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.srcrs.domain.Cookie;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 封装的网络请求请求工具类
 *
 * @author srcrs
 * @Time 2020-10-31
 */
public class Request {
    private static final Logger LOGGER = LoggerFactory.getLogger(Request.class);
    private static final Cookie cookie = Cookie.getInstance();

    /**
     * 使用连接池管理 HttpClient 资源，防止连接泄露
     */
    private static final CloseableHttpClient CLIENT;

    static {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(20);
        cm.setDefaultMaxPerRoute(10);
        RequestConfig defaultConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .setConnectTimeout(5000)
                .setSocketTimeout(5000)
                .build();
        CLIENT = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(defaultConfig)
                .build();
    }

    private Request() {}

    /**
     * 发送get请求
     *
     * @param url 请求的地址，包括参数
     * @return JSONObject
     */
    public static JSONObject get(String url) {
        HttpGet httpGet = new HttpGet(url);
        setHeader(httpGet);
        try (CloseableHttpResponse response = CLIENT.execute(httpGet)) {
            HttpEntity entity = response.getEntity();
            String respContent = EntityUtils.toString(entity, "UTF-8");
            return JSONObject.parseObject(respContent);
        } catch (Exception e) {
            LOGGER.error("get请求错误 -- URL: {}, Error: {}", url, e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * 发送post请求
     *
     * @param url  请求的地址
     * @param body 携带的参数
     * @return JSONObject
     */
    public static JSONObject post(String url, String body) {
        HttpPost httpPost = new HttpPost(url);
        setHeader(httpPost);
        httpPost.setEntity(new StringEntity(body, "UTF-8"));
        try (CloseableHttpResponse response = CLIENT.execute(httpPost)) {
            HttpEntity entity = response.getEntity();
            String respContent = EntityUtils.toString(entity, "UTF-8");
            return JSONObject.parseObject(respContent);
        } catch (Exception e) {
            LOGGER.error("post请求错误 -- URL: {}, Error: {}", url, e.getMessage());
            return new JSONObject();
        }
    }

    private static void setHeader(org.apache.http.client.methods.HttpRequestBase request) {
        request.addHeader("connection", "keep-alive");
        request.addHeader("Content-Type", "application/x-www-form-urlencoded");
        request.addHeader("charset", "UTF-8");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36");
        request.addHeader("Cookie", cookie.getCookie());
        if (request instanceof HttpPost) {
            request.addHeader("Host", "tieba.baidu.com");
        }
    }

    /**
     * 校验贴吧是否存在
     * @param name 贴吧名
     * @return Boolean
     */
    public static Boolean isTiebaNotExist(String name) {
        String url = "https://tieba.baidu.com/f?ie=utf-8&kw=" + name + "&fr=search";
        HttpGet request = new HttpGet(url);
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36");

        try (CloseableHttpResponse response = CLIENT.execute(request)) {
            StringBuilder result = new StringBuilder();
            try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                String line;
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }
            }
            if (result.toString().contains("很抱歉，没有找到相关内容")) {
                LOGGER.info("{} 不存在", name);
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("校验贴吧是否存在时出错: {}", e.getMessage());
            return false;
        }
    }
}
