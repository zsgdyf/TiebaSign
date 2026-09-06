package top.srcrs;

import org.junit.Assert;
import org.junit.Test;
import top.srcrs.domain.Cookie;
import top.srcrs.util.Encryption;

/**
 * 贴吧签到工具单元测试
 *
 * @author srcrs, zsgdyf
 */
public class TiebaSignTest {

    /**
     * 测试 MD5 计算与签名工具类
     */
    @Test
    public void testEncryption() {
        String input = "tiebaclient!!!";
        String md5 = Encryption.enCodeMd5(input);
        Assert.assertNotNull(md5);
        Assert.assertEquals(32, md5.length());

        String md5Upper = Encryption.enCodeMd5Upper(input);
        Assert.assertEquals(md5.toUpperCase(), md5Upper);
    }

    /**
     * 测试 Cookie 凭据管理
     */
    @Test
    public void testCookie() {
        Cookie cookie = Cookie.getInstance();
        cookie.setBDUSS(" test_bduss ");
        Assert.assertEquals("test_bduss", cookie.getBDUSS());
        Assert.assertEquals("BDUSS=test_bduss", cookie.getCookie());
    }

    /**
     * 测试主入口在没有入参且无环境变量时能安全退出，不抛出数组越界等未捕获异常
     */
    @Test
    public void testMainWithoutArgs() {
        // 执行无参数的 main 方法，预期正常返回而不会抛出 ArrayIndexOutOfBoundsException
        Run.main(new String[]{});
    }
}
