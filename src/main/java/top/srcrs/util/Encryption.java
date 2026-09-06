package top.srcrs.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 字符串加密与哈希计算工具类
 *
 * @author srcrs
 * @Time 2020-10-31
 */
public class Encryption {
    /** 获取日志记录器对象 */
    private static final Logger LOGGER = LoggerFactory.getLogger(Encryption.class);

    private Encryption() {}

    /**
     * 对字符串进行 MD5 加密
     *
     * @param str 传入一个字符串
     * @return String 加密后的字符串
     */
    public static String enCodeMd5(String str) {
        if (str == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("字符串进行 MD5 加密错误", e);
            return "";
        }
    }

    /**
     * 对字符串进行 MD5 加密并转为大写
     *
     * @param str 传入一个字符串
     * @return 大写的 MD5 字符串
     */
    public static String enCodeMd5Upper(String str) {
        return enCodeMd5(str).toUpperCase();
    }
}
