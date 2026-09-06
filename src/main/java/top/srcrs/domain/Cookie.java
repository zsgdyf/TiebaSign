package top.srcrs.domain;

/**
 * 用户身份凭证与 Cookie 封装
 *
 * @author srcrs
 * @Time 2020-10-31
 */
public class Cookie {
    private static final Cookie INSTANCE = new Cookie();
    private String bduss = "";

    private Cookie() {}

    public static Cookie getInstance() {
        return INSTANCE;
    }

    public String getBDUSS() {
        return bduss;
    }

    public void setBDUSS(String bduss) {
        this.bduss = (bduss == null) ? "" : bduss.trim();
    }

    public String getCookie() {
        return "BDUSS=" + bduss;
    }
}
