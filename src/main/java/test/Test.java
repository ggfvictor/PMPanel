package test;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import project.daihao18.panel.PanelApplication;
import project.daihao18.panel.common.schedule.tasks.DailyJobTaskService;
import project.daihao18.panel.common.utils.CommonUtil;
import project.daihao18.panel.entity.User;
import project.daihao18.panel.service.UserService;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @ClassName: Test
 * @Description:
 * @Author: code18
 * @Date: 2020-11-12 21:12
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = PanelApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class Test {

    @Autowired
    private UserService userService;

    @Autowired
    private DailyJobTaskService dailyJobTaskService;

    @org.junit.Test
    @Transactional
    public void update() {
        List<User> userList = userService.list();
        for (User user : userList) {
            user.setInviteCode(RandomUtil.randomString(4));
            user.setLink(RandomUtil.randomString(10));
        }
        userService.updateBatchById(userList);
    }

    @org.junit.Test
    public void file() {
        FileReader fileReader = new FileReader("config/clash");
        List<String> strings = fileReader.readLines();
        for (String s : strings) {
            System.out.println(s);
        }
    }

    @org.junit.Test
    public void md5() {
        System.out.println(DigestUtil.md5Hex("1232333666661607758745"));
    }

    @org.junit.Test
    public void sha256() {
        String pwd = DigestUtil.sha256Hex("123" + "1607758745");
        System.out.println(pwd.substring(5, 50));
    }


    @org.junit.Test
    public void doJob() {
        dailyJobTaskService.monthlyJob();
    }

    @org.junit.Test
    public void registerUser() {
        Date start = new Date();
        int count = userService.count();
        List<User> userList = new ArrayList<>();
        User user = null;
        for (int i = count; i <= 4000; i++) {
            user = new User();
            user.setId(i + 1);
            user.setEmail(i + "@qq.com");
            userList.add(user);
            user = null;
        }
        userService.saveBatch(userList);
        Date end = new Date();
        System.out.println(DateUtil.between(start, end, DateUnit.SECOND));
    }

    @org.junit.Test
    public void updateUser() {
        Date start = new Date();
        List<User> userList = userService.list();
        userList.remove(0);
        for (User user : userList) {
            user.setU(2L);
            user.setD(2L);
            user.setP(2L);
            user.setTransferEnable(2L);
        }
        userService.updateBatchById(userList, 10000);
        Date end = new Date();
        System.out.println(DateUtil.between(start, end, DateUnit.SECOND));
    }

    @org.junit.Test
    public void uuid() {
        UUID namespace_dns = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        String info = "25724|tQ20vL";

        ByteBuffer buffer = ByteBuffer.wrap(new byte[16 + info.getBytes().length]);
        buffer.putLong(namespace_dns.getMostSignificantBits());
        buffer.putLong(namespace_dns.getLeastSignificantBits());
        buffer.put(info.getBytes());

        byte[] uuidBytes = buffer.array();

        System.out.println(UUID.nameUUIDFromBytes(uuidBytes));
    }

    @org.junit.Test
    public void testSubIdEncrpyt() {
        Integer id = 100;
        String s = CommonUtil.subsEncryptId(id);
        System.out.println(s);
        Integer deId = CommonUtil.subsDecryptId("dbaafdagalkgadgjlag");
        System.out.println(deId);
        String link = CommonUtil.subsLinkDecryptId("dbaafdagalkgadgjlag");
        System.out.println(link);
    }
}