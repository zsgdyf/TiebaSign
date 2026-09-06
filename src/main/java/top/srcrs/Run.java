package top.srcrs;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.srcrs.domain.Cookie;
import top.srcrs.util.Encryption;
import top.srcrs.util.Request;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 贴吧自动签到核心运行入口
 *
 * @author srcrs, zsgdyf
 * @Time 2020-10-31
 */
public class Run {
    private static final Logger LOGGER = LoggerFactory.getLogger(Run.class);

    /** 获取用户的 tbs (加密令牌) 接口 */
    private static final String TBS_URL = "https://tieba.baidu.com/dc/common/tbs";

    /** 移动端关注贴吧接口 (支持翻页，突破200个上限) */
    private static final String LIKE_MOBILE_URL = "https://c.tieba.baidu.com/c/f/forum/like";

    /** 网页端关注贴吧备用接口 */
    private static final String LIKE_WEB_URL = "https://tieba.baidu.com/mo/q/newmoindex";

    /** 贴吧移动端签到接口 */
    private static final String SIGN_URL = "https://c.tieba.baidu.com/c/c/forum/sign";

    /** 待签到的贴吧列表 */
    private final List<String> pendingList = new ArrayList<>();

    /** 本次运行签到成功的贴吧列表 */
    private final List<String> successList = new ArrayList<>();

    /** 已经签到过的贴吧列表 */
    private final List<String> alreadySignedList = new ArrayList<>();

    /** 被屏蔽或失效的贴吧列表 */
    private final List<String> shieldList = new ArrayList<>();

    /** 最终重试依然失败的贴吧列表 */
    private final Set<String> failedSet = new LinkedHashSet<>();

    /** 用户的 tbs */
    private String tbs = "";

    /** 关注贴吧总数 */
    private int totalForumNum = 0;

    public static void main(String[] args) {
        String bduss = (args.length > 0 && !args[0].trim().isEmpty()) ? args[0].trim() : System.getenv("BDUSS");
        String sckey = (args.length > 1 && !args[1].trim().isEmpty()) ? args[1].trim() : System.getenv("SCKEY");

        if (bduss == null || bduss.trim().isEmpty()) {
            LOGGER.error("未检测到有效的 BDUSS！请在命令行参数或环境变量中配置 BDUSS");
            return;
        }

        Cookie.getInstance().setBDUSS(bduss);

        Run runner = new Run();
        if (!runner.getTbs()) {
            LOGGER.error("获取会话令牌 tbs 失败，无法继续签到，请检查 BDUSS 是否有效或是否过期");
            return;
        }

        runner.fetchFollowList();
        runner.executeSign();
        runner.printSummary();

        if (sckey != null && !sckey.trim().isEmpty()) {
            runner.sendNotification(sckey);
        }
    }

    /**
     * 请求百度接口获取会话 token (tbs)
     *
     * @return true 获取成功，false 获取失败
     */
    public boolean getTbs() {
        try {
            JSONObject jsonObject = Request.get(TBS_URL);
            if ("1".equals(jsonObject.getString("is_login"))) {
                tbs = jsonObject.getString("tbs");
                LOGGER.info("获取 tbs 成功: {}", tbs);
                return true;
            } else {
                LOGGER.warn("获取 tbs 失败，当前未处于登录状态: {}", jsonObject);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("获取 tbs 发生异常", e);
            return false;
        }
    }

    /**
     * 拉取用户关注的全部贴吧列表
     * 优先采用移动端翻页接口突破 200 个限制，失败时自动降级为网页版接口
     */
    public void fetchFollowList() {
        LOGGER.info("正在获取关注的贴吧列表...");
        Set<String> collectedForums = new LinkedHashSet<>();

        try {
            int pageNo = 1;
            boolean hasMore = true;

            while (hasMore) {
                long timestamp = System.currentTimeMillis() / 1000;
                Map<String, String> params = new TreeMap<>();
                params.put("BDUSS", Cookie.getInstance().getBDUSS());
                params.put("_client_id", "wappc_1534235498291_488");
                params.put("_client_type", "2");
                params.put("_client_version", "9.7.8.0");
                params.put("_phone_imei", "000000000000000");
                params.put("from", "1008621y");
                params.put("model", "MI+5");
                params.put("net_type", "1");
                params.put("page_no", String.valueOf(pageNo));
                params.put("page_size", "200");
                params.put("timestamp", String.valueOf(timestamp));
                params.put("vcode_tag", "11");

                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
                String sign = Encryption.enCodeMd5Upper(sb.toString() + "tiebaclient!!!");

                StringBuilder bodyBuilder = new StringBuilder();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    bodyBuilder.append(entry.getKey()).append("=")
                            .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).append("&");
                }
                bodyBuilder.append("sign=").append(sign);

                JSONObject resp = Request.post(LIKE_MOBILE_URL, bodyBuilder.toString());
                if (resp == null || !resp.containsKey("forum_list")) {
                    LOGGER.warn("移动端获取关注贴吧列表第 {} 页返回异常，准备降级尝试备用接口", pageNo);
                    break;
                }

                JSONObject forumListObj = resp.getJSONObject("forum_list");
                if (forumListObj != null) {
                    for (String key : new String[]{"non-gconforum", "gconforum"}) {
                        Object obj = forumListObj.get(key);
                        if (obj instanceof JSONArray) {
                            JSONArray arr = (JSONArray) obj;
                            for (int i = 0; i < arr.size(); i++) {
                                JSONObject item = arr.getJSONObject(i);
                                String name = item.getString("name");
                                if (name != null && !name.trim().isEmpty()) {
                                    collectedForums.add(name.trim());
                                }
                            }
                        }
                    }
                }

                hasMore = "1".equals(resp.getString("has_more"));
                pageNo++;
            }
        } catch (Exception e) {
            LOGGER.warn("移动端接口获取关注贴吧异常，准备使用网页端备用接口", e);
        }

        // 如果移动端接口未能成功获取到贴吧列表，降级使用网页端接口
        if (collectedForums.isEmpty()) {
            LOGGER.info("正在使用网页端备用接口获取贴吧列表...");
            try {
                JSONObject webResp = Request.get(LIKE_WEB_URL);
                if (webResp.getJSONObject("data") != null) {
                    JSONArray likeForums = webResp.getJSONObject("data").getJSONArray("like_forum");
                    if (likeForums != null) {
                        for (int i = 0; i < likeForums.size(); i++) {
                            JSONObject item = likeForums.getJSONObject(i);
                            String name = item.getString("forum_name");
                            if (name != null && !name.trim().isEmpty()) {
                                if ("1".equals(item.getString("is_sign"))) {
                                    alreadySignedList.add(name.trim());
                                } else {
                                    collectedForums.add(name.trim());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("网页端备用接口获取贴吧列表失败", e);
            }
        }

        pendingList.addAll(collectedForums);
        totalForumNum = pendingList.size() + alreadySignedList.size();
        LOGGER.info("共拉取到 {} 个关注的贴吧 (其中 {} 个已签到，{} 个待签到)",
                totalForumNum, alreadySignedList.size(), pendingList.size());
    }

    /**
     * 执行分轮次批量签到
     */
    public void executeSign() {
        int maxRounds = 5;
        int currentRound = 1;

        while (!pendingList.isEmpty() && currentRound <= maxRounds) {
            LOGGER.info("===== 开始第 {} 轮签到 (剩余 {} 个贴吧) =====", currentRound, pendingList.size());
            Iterator<String> iterator = pendingList.iterator();
            int count = 0;

            while (iterator.hasNext()) {
                String forumName = iterator.next();
                count++;

                // 随机休眠 1.2 ~ 2.5 秒，模拟正常请求
                int sleepMs = ThreadLocalRandom.current().nextInt(1200, 2500);
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }

                // 每处理 10 个贴吧额外休息 3~6 秒，降低被识别频率
                if (count % 10 == 0) {
                    int extraSleep = ThreadLocalRandom.current().nextInt(3000, 6000);
                    LOGGER.info("已处理 {} 个贴吧，额外休眠 {} 毫秒以防风控...", count, extraSleep);
                    try {
                        TimeUnit.MILLISECONDS.sleep(extraSleep);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                SignResult result = signSingleForum(forumName);
                if (result == SignResult.SUCCESS) {
                    iterator.remove();
                    successList.add(forumName);
                    failedSet.remove(forumName);
                    LOGGER.info("【{}】签到成功", forumName);
                } else if (result == SignResult.ALREADY_SIGNED) {
                    iterator.remove();
                    alreadySignedList.add(forumName);
                    failedSet.remove(forumName);
                    LOGGER.info("【{}】今日已经签到过", forumName);
                } else if (result == SignResult.SHIELDED) {
                    iterator.remove();
                    shieldList.add(forumName);
                    failedSet.remove(forumName);
                    LOGGER.warn("【{}】贴吧已被屏蔽或失效，跳过后续重试", forumName);
                } else {
                    failedSet.add(forumName);
                    LOGGER.warn("【{}】签到未成功，将在下一轮重试", forumName);
                }
            }

            // 如果还有待签到的贴吧，等待 45 秒并重新刷新 tbs
            if (!pendingList.isEmpty() && currentRound < maxRounds) {
                LOGGER.info("第 {} 轮签到完成，等待 45 秒后开始下一轮重试...", currentRound);
                try {
                    Thread.sleep(45000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                getTbs();
            }
            currentRound++;
        }
    }

    /**
     * 单个贴吧签到网络请求与结果解析
     *
     * @param forumName 贴吧名称
     * @return 签到结果枚举
     */
    private SignResult signSingleForum(String forumName) {
        try {
            String sign = Encryption.enCodeMd5("kw=" + forumName + "tbs=" + tbs + "tiebaclient!!!");
            String body = "kw=" + URLEncoder.encode(forumName, StandardCharsets.UTF_8)
                    + "&tbs=" + URLEncoder.encode(tbs, StandardCharsets.UTF_8)
                    + "&sign=" + sign;

            JSONObject response = Request.post(SIGN_URL, body);
            if (response == null) {
                return SignResult.FAILED;
            }

            String errorCode = response.getString("error_code");
            if ("0".equals(errorCode)) {
                return SignResult.SUCCESS;
            } else if ("160002".equals(errorCode)) {
                return SignResult.ALREADY_SIGNED;
            } else if ("340006".equals(errorCode)) {
                return SignResult.SHIELDED;
            } else {
                LOGGER.warn("【{}】签到返回状态码: {}, 错误信息: {}",
                        forumName, errorCode, response.getString("error_msg"));
                return SignResult.FAILED;
            }
        } catch (Exception e) {
            LOGGER.error("【{}】签到请求异常", forumName, e);
            return SignResult.FAILED;
        }
    }

    /**
     * 打印最终运行汇总日志
     */
    public void printSummary() {
        LOGGER.info("================ 签到统计汇总 ================");
        LOGGER.info("贴吧总数: {}", totalForumNum);
        LOGGER.info("本次成功: {}", successList.size());
        LOGGER.info("已经签到: {}", alreadySignedList.size());
        LOGGER.info("屏蔽失效: {}", shieldList.size());
        LOGGER.info("签到失败: {} (列表: {})", failedSet.size(), failedSet);
        LOGGER.info("==============================================");
    }

    /**
     * 推送运行结果到微信（支持 Server 酱与 Server 酱 Turbo 版）
     *
     * @param sckey 密钥
     */
    public void sendNotification(String sckey) {
        if (sckey == null || sckey.trim().isEmpty()) {
            return;
        }
        sckey = sckey.trim();

        String title = String.format("贴吧签到: 成功 %d/总 %d (失败 %d)",
                successList.size() + alreadySignedList.size(), totalForumNum, failedSet.size());

        StringBuilder desp = new StringBuilder();
        desp.append("### 贴吧自动签到运行报告\n\n");
        desp.append("- **关注总数**：").append(totalForumNum).append("\n");
        desp.append("- **本次签到**：").append(successList.size()).append("\n");
        desp.append("- **今日已签**：").append(alreadySignedList.size()).append("\n");
        desp.append("- **屏蔽失效**：").append(shieldList.size()).append("\n");
        desp.append("- **最终失败**：").append(failedSet.size()).append("\n\n");

        if (!failedSet.isEmpty()) {
            desp.append("**失败贴吧列表**：\n");
            for (String f : failedSet) {
                desp.append("- ").append(f).append("\n");
            }
        }

        try {
            String pushUrl;
            String body;
            // 自动判断 Server 酱 Turbo 版 (SCT 开头) 或旧版
            if (sckey.toUpperCase().startsWith("SCT")) {
                pushUrl = "https://sctapi.ftqq.com/" + sckey + ".send";
                body = "title=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                        + "&desp=" + URLEncoder.encode(desp.toString(), StandardCharsets.UTF_8);
            } else {
                pushUrl = "https://sc.ftqq.com/" + sckey + ".send";
                body = "text=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                        + "&desp=" + URLEncoder.encode(desp.toString(), StandardCharsets.UTF_8);
            }

            JSONObject resp = Request.post(pushUrl, body);
            LOGGER.info("消息推送响应: {}", resp);
        } catch (Exception e) {
            LOGGER.error("推送消息至微信失败", e);
        }
    }

    /** 签到单项结果状态 */
    private enum SignResult {
        SUCCESS,
        ALREADY_SIGNED,
        SHIELDED,
        FAILED
    }
}
