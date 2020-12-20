package project.daihao18.panel.serviceImpl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayApiException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.daihao18.panel.common.payment.alipay.Alipay;
import project.daihao18.panel.common.response.Result;
import project.daihao18.panel.common.response.ResultCodeEnum;
import project.daihao18.panel.common.schedule.CronTaskRegistrar;
import project.daihao18.panel.common.schedule.SchedulingRunnable;
import project.daihao18.panel.common.utils.FlowSizeConverterUtil;
import project.daihao18.panel.entity.*;
import project.daihao18.panel.service.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @ClassName: AdminServiceImpl
 * @Description:
 * @Author: code18
 * @Date: 2020-11-28 15:32
 */
@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private ConfigService configService;

    @Autowired
    private Alipay alipay;

    @Autowired
    private SsNodeService ssNodeService;

    @Autowired
    private DetectListService detectListService;

    @Autowired
    private NodeWithDetectService nodeWithDetectService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlanService planService;

    @Autowired
    private TutorialService tutorialService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private FundsService fundsService;

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private AliveIpService aliveIpService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private CronTaskRegistrar cronTaskRegistrar;

    @Override
    public Result getDashboardInfo() {
        Map<String, Object> map = new HashMap<>();
        // TODO 获取待办工单数量
        // 获取用户数
        map.put("userCount", userService.count());
        map.put("todayRegisterCount", userService.getRegisterCountByDateToNow(DateUtil.beginOfDay(new Date())));
        // 获取收入
        map.put("monthIncome", orderService.getMonthIncome());
        map.put("todayIncome", orderService.getTodayIncome());
        return Result.ok().data(map);
    }

    @Override
    public Result cleanRedisCache() {
        redisService.deleteByKeys("panel::config::*");
        redisService.deleteByKeys("panel::node::*");
        redisService.deleteByKeys("panel::detect::*");
        redisService.deleteByKeys("panel::tutorial::*");
        redisService.deleteByKeys("panel::plan::*");
        redisService.deleteByKeys("panel::site::*");
        redisService.deleteByKeys("panel::user::*");
        return Result.ok().message("缓存已清理").messageEnglish("Cache is already cleaned");
    }

    @Override
    public Result getSiteConfig() {
        String[] keys = {"siteName", "siteUrl", "subUrl", "regEnable", "inviteOnly", "mailRegEnable", "mailLimit", "mailType", "mailConfig"};
        Map<String, Object> siteConfig = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            String value = configService.getValueByName(keys[i]);
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                siteConfig.put(keys[i], Boolean.parseBoolean(value));
            } else if (NumberUtil.isInteger(value)) {
                siteConfig.put(keys[i], Integer.parseInt(value));
            } else {
                // mailConfig 转成map
                if ("mailConfig".equalsIgnoreCase(keys[i])) {
                    Map<String, Object> map = JSONUtil.toBean(value, Map.class);
                    siteConfig.put(keys[i], map);
                } else {
                    siteConfig.put(keys[i], value);
                }
            }
        }
        return Result.ok().data("siteConfig", siteConfig);
    }

    @Override
    public Result getRegisterConfig() {
        String[] keys = {"enableEmailSuffix", "userPortRange", "inviteCount", "inviteRate", "enableWithdraw", "minWithdraw", "withdrawRate"};
        Map<String, Object> registerConfig = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            String value = configService.getValueByName(keys[i]);
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                registerConfig.put(keys[i], Boolean.parseBoolean(value));
            } else {
                if ("userPortRange".equalsIgnoreCase(keys[i])) {
                    registerConfig.put("userMinPort", value.split(":")[0]);
                    registerConfig.put("userMaxPort", value.split(":")[1]);
                } else {
                    registerConfig.put(keys[i], value);
                }
            }
        }
        return Result.ok().data("registerConfig", registerConfig);
    }

    @Override
    public Result getPaymentConfig() {
        String[] keys = {"alipay", "wxpay", "alipayConfig"};
        Map<String, Object> paymentConfig = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            String value = configService.getValueByName(keys[i]);
            if ("alipay".equalsIgnoreCase(keys[i]) || "wxpay".equalsIgnoreCase(keys[i])) {
                paymentConfig.put(keys[i], value);
            } else {
                // 支付详细配置,转成map
                Map<String, Object> map = JSONUtil.toBean(value, Map.class);
                map.put("isCertMode", Boolean.parseBoolean(map.get("isCertMode").toString()));
                map.put("web", Boolean.parseBoolean(map.get("web").toString()));
                map.put("wap", Boolean.parseBoolean(map.get("wap").toString()));
                map.put("f2f", Boolean.parseBoolean(map.get("f2f").toString()));
                paymentConfig.put(keys[i], map);
            }
        }
        return Result.ok().data("paymentConfig", paymentConfig);
    }

    @Override
    public Result getOtherConfig() {
        String[] keys = {"muSuffix", "userTrafficLogLimitDays"};
        Map<String, Object> otherConfig = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            String value = configService.getValueByName(keys[i]);
            if (NumberUtil.isInteger(value)) {
                otherConfig.put(keys[i], Integer.parseInt(value));
            } else {
                otherConfig.put(keys[i], value);
            }
        }
        return Result.ok().data("otherConfig", otherConfig);
    }

    @Override
    @Transactional
    public Result updateValueByName(Config config) throws AlipayApiException {
        if (configService.updateValueByName(config.getName(), config.getValue())) {
            // 如果config.getName() == "alipayConfig",去设置alipayConfig
            if ("alipayConfig".equals(config.getName())) {
                alipay.refreshAlipayConfig();
            }
            return Result.ok().message("更新成功").messageEnglish("Update Successfully");
        } else {
            // 插入
            if (configService.save(config)) {
                return Result.ok().message("更新成功").messageEnglish("Update Successfully");
            } else {
                return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
            }
        }
    }

    @Override
    public Result getNode(HttpServletRequest request) {
        Integer pageNo = Integer.parseInt(request.getParameter("pageNo"));
        Integer pageSize = Integer.parseInt(request.getParameter("pageSize"));
        String sortField = request.getParameter("sortField");
        String sortOrder = request.getParameter("sortOrder");
        IPage<SsNode> page = ssNodeService.getPageNode(pageNo, pageSize, sortField, sortOrder);
        List<SsNode> ssNodes = page.getRecords();
        // 查询节点对应的在线ip数以及ip
        for (SsNode node : ssNodes) {
            QueryWrapper<AliveIp> aliveIpQueryWrapper = new QueryWrapper<>();
            aliveIpQueryWrapper.eq("nodeid", node.getId());
            List<AliveIp> aliveIps = aliveIpService.list(aliveIpQueryWrapper);
            node.setOnline(aliveIps.size());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("data", ssNodes);
        map.put("pageNo", page.getCurrent());
        map.put("totalCount", page.getTotal());
        return Result.ok().data("data", map);
    }

    @Override
    public Result getNodeInfoByNodeId(Integer nodeId) {
        SsNode node = ssNodeService.getById(nodeId);

        QueryWrapper<AliveIp> aliveIpQueryWrapper = new QueryWrapper<>();
        aliveIpQueryWrapper.eq("nodeid", node.getId());
        List<AliveIp> aliveIps = aliveIpService.list(aliveIpQueryWrapper);
        List<Map<String, Object>> onlineIps = new ArrayList<>();
        for (AliveIp aliveIp : aliveIps) {
            Map<String, Object> userMapIp = new HashMap<>();
            userMapIp.put("userId", aliveIp.getUserid());
            userMapIp.put("ip", aliveIp.getIp());
            onlineIps.add(userMapIp);
        }
        node.setOnlineIps(onlineIps);
        return Result.ok().data("info", node);
    }

    @Override
    public Result getAllNodes() {
        List<SsNode> ssNodeList = ssNodeService.getAllNodes();
        List<SsNode> detectNodeList = new ArrayList<>();
        // 过滤掉当前已配置审计的节点
        List<Integer> nodeIds = nodeWithDetectService.getNodeId();
        Iterator<SsNode> iterator = ssNodeList.iterator();
        while (iterator.hasNext()) {
            SsNode node = iterator.next();
            for (int i : nodeIds) {
                if (node.getId().equals(i)) {
                    detectNodeList.add(node);
                    iterator.remove();
                }
            }
        }
        return Result.ok().data("allNodes", ssNodeList).data("detectNodeList", detectNodeList);
    }

    @Override
    @Transactional
    public Result addNode(SsNode ssNode) {
        ssNode.setCustomMethod("1");
        ssNode.setNodeSpeedlimit(0.00);
        ssNode.setNodeConnector(0);
        ssNode.setNodeBandwidth(0L);
        ssNode.setNodeBandwidthLimit(0L);
        ssNode.setBandwidthlimitResetday(0);
        ssNode.setCustomRss(1);
        if (ssNode.getSort() == 0) {
            ssNode.setPort(Integer.parseInt(ssNode.getServer().split(";")[1].split("#")[0].split("=")[1]));
            ssNode.setMuOnly(1);
        } else if (ssNode.getSort() == 11) {
            ssNode.setMuOnly(-1);
        }
        if (ssNodeService.save(ssNode)) {
            redisService.deleteByKeys("panel::node::*");
            return Result.ok().message("添加成功").messageEnglish("Add successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result editNode(SsNode ssNode) {
        if (ssNode.getSort() == 0) {
            ssNode.setPort(Integer.parseInt(ssNode.getServer().split(";")[1].split("#")[0].split("=")[1]));
            ssNode.setMuOnly(1);
        } else if (ssNode.getSort() == 11) {
            ssNode.setMuOnly(-1);
        }
        if (ssNodeService.updateById(ssNode)) {
            redisService.deleteByKeys("panel::node::*");
            return Result.ok().message("修改成功").messageEnglish("Update successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result deleteNodeById(Integer id) {
        if (ssNodeService.removeById(id)) {
            redisService.deleteByKeys("panel::node::*");
            return Result.ok().message("删除成功").messageEnglish("Delete successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public Result getAllDetects() {
        List<DetectList> detectLists = detectListService.getAllDetects();
        return Result.ok().data("allDetects", detectLists);
    }

    @Override
    public Result getDetect(Integer pageNo, Integer pageSize) {
        IPage<DetectList> page = detectListService.getPageDetect(pageNo, pageSize);
        Map<String, Object> map = new HashMap<>();
        map.put("data", page.getRecords());
        map.put("pageNo", page.getCurrent());
        map.put("totalCount", page.getTotal());
        return Result.ok().data("data", map);
    }

    @Override
    @Transactional
    public Result addDetect(DetectList detectList) {
        if (detectListService.save(detectList)) {
            redisService.deleteByKeys("panel::detect::*");
            return Result.ok().message("添加成功").messageEnglish("Add successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result editDetect(DetectList detectList) {
        if (detectListService.updateById(detectList)) {
            redisService.deleteByKeys("panel::detect::*");
            return Result.ok().message("修改成功").messageEnglish("Update successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result deleteDetectById(Integer id) {
        if (detectListService.removeById(id)) {
            redisService.deleteByKeys("panel::detect::*");
            return Result.ok().message("删除成功").messageEnglish("Delete successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public Result getNodeWithDetect(Integer pageNo, Integer pageSize) {
        Map<String, Object> map = nodeWithDetectService.getNodeWithDetect(pageNo, pageSize);
        return Result.ok().data("data", map);
    }

    @Override
    @Transactional
    public Result addNodeWithDetect(Map<String, Object> map) {
        Double doubleNodeId = Double.parseDouble(map.get("nodeId").toString());
        Integer nodeId = doubleNodeId.intValue();
        ArrayList<String> detectIds = (ArrayList<String>) map.get("detectIds");
        List<Integer> detectListId = detectIds.stream().map(Integer::parseInt).collect(Collectors.toList());
        // 设置需要进行存储的NodeDetectList的List
        List<NodeWithDetect> list = new ArrayList<>();
        for (int i : detectListId) {
            NodeWithDetect item = new NodeWithDetect();
            item.setNodeId(nodeId);
            item.setDetectListId(i);
            list.add(item);
        }
        // 批量保存更新
        if (nodeWithDetectService.saveBatch(list)) {
            redisService.deleteByKeys("panel::detect::*");
            return Result.ok().message("添加成功").messageEnglish("Add successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result editNodeWithDetect(Map<String, Object> map) {
        Double doubleNodeId = Double.parseDouble(map.get("nodeId").toString());
        Integer nodeId = doubleNodeId.intValue();
        // 删除原来的记录
        nodeWithDetectService.deleteByNodeId(nodeId);
        ArrayList<String> detectIds = (ArrayList<String>) map.get("detectIds");
        List<Integer> detectListId = detectIds.stream().map(Integer::parseInt).collect(Collectors.toList());
        // 设置需要进行存储的NodeDetectList的List
        List<NodeWithDetect> list = new ArrayList<>();
        for (int i : detectListId) {
            NodeWithDetect item = new NodeWithDetect();
            item.setNodeId(nodeId);
            item.setDetectListId(i);
            list.add(item);
        }
        // 批量保存更新
        if (nodeWithDetectService.saveBatch(list)) {
            redisService.deleteByKeys("panel::detect::*");
            return Result.ok().message("修改成功").messageEnglish("Update successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result deleteNodeWithDetectById(Integer id) {
        if (nodeWithDetectService.deleteByNodeId(id)) {
            redisService.deleteByKeys("panel::detect::*");
            return Result.ok().message("删除成功").messageEnglish("Delete successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public Result getUser(HttpServletRequest request) {
        // 获取查询参数
        Integer id = null;
        if (ObjectUtil.isNotEmpty(request.getParameter("id"))) {
            id = Integer.parseInt(request.getParameter("id"));
        }
        String email = null;
        if (ObjectUtil.isNotEmpty(request.getParameter("email"))) {
            email = request.getParameter("email");
        }
        Integer clazz = null;
        if (ObjectUtil.isNotEmpty(request.getParameter("clazz"))) {
            clazz = Integer.parseInt(request.getParameter("clazz"));
        }
        Date expireIn = null;
        if (ObjectUtil.isNotEmpty(request.getParameter("expire"))) {
            LocalDateTime dateTime = DateUtil.parseDate(request.getParameter("expire").replace("\"", "")).toTimestamp().toLocalDateTime().plusHours(23).plusMinutes(59).plusSeconds(59);
            expireIn = Date.from(dateTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        }
        Integer role = null;
        if (ObjectUtil.isNotEmpty(request.getParameter("role"))) {
            role = Integer.parseInt(request.getParameter("role"));
        }
        Integer enable = null;
        if (ObjectUtil.isNotEmpty(request.getParameter("enable"))) {
            enable = Integer.parseInt(request.getParameter("enable"));
        }
        // 带参数查询,查询结果不缓存
        boolean cacheFlag = false;
        if (ObjectUtil.isNotEmpty(id) || ObjectUtil.isNotEmpty(email) || ObjectUtil.isNotEmpty(clazz) || ObjectUtil.isNotEmpty(expireIn) || ObjectUtil.isNotEmpty(role) || ObjectUtil.isNotEmpty(enable)) {
            // 先删除缓存
            redisService.deleteByKeys("panel::user::users::*");
            cacheFlag = true;
        }
        return userService.getUserByPageAndQueryParam(request, Integer.parseInt(request.getParameter("pageNo")), Integer.parseInt(request.getParameter("pageSize")), cacheFlag);
    }

    @Override
    public Result getUserDetail(Integer id) {
        Map<String, Object> info = new HashMap<>();
        User user = userService.getUserById(id, false);
        info.put("user", user);
        return Result.ok().data("info", info);
    }

    @Override
    @Transactional
    public Result updateUserById(User user) {
        User existUser = userService.getById(user.getId());
        // 重新设置user的信息
        if (ObjectUtil.notEqual(existUser.getEmail(), user.getEmail())) {
            existUser.setEmail(user.getEmail());
        }
        if (ObjectUtil.isNotEmpty(user.getPassword())) {
            // 重新设置密码
            existUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        if (ObjectUtil.notEqual(existUser.getClazz(), user.getClazz())) {
            existUser.setClazz(user.getClazz());
        }
        if (ObjectUtil.notEqual(existUser.getExpireIn(), user.getExpireIn())) {
            existUser.setExpireIn(user.getExpireIn());
        }
        if (ObjectUtil.notEqual(existUser.getMoney(), user.getMoney())) {
            existUser.setMoney(user.getMoney());
        }
        if (ObjectUtil.notEqual(existUser.getTransferEnable(), FlowSizeConverterUtil.GbToBytes(user.getTransferEnableGb()))) {
            existUser.setTransferEnable(FlowSizeConverterUtil.GbToBytes(user.getTransferEnableGb()));
        }
        if (ObjectUtil.notEqual(existUser.getInviteCount(), user.getInviteCount())) {
            existUser.setInviteCount(user.getInviteCount());
        }
        if (ObjectUtil.notEqual(existUser.getInviteCycleRate(), user.getInviteCycleRate())) {
            existUser.setInviteCycleRate(user.getInviteCycleRate());
        }
        if (ObjectUtil.notEqual(existUser.getNodeSpeedlimit(), user.getNodeSpeedlimit())) {
            existUser.setNodeSpeedlimit(user.getNodeSpeedlimit());
        }
        if (ObjectUtil.notEqual(existUser.getNodeConnector(), user.getNodeConnector())) {
            existUser.setNodeConnector(user.getNodeConnector());
        }
        if (ObjectUtil.notEqual(existUser.getNodeGroup(), user.getNodeGroup())) {
            existUser.setNodeGroup(user.getNodeGroup());
        }
        if (ObjectUtil.notEqual(existUser.getIsAdmin(), user.getIsAdmin())) {
            existUser.setIsAdmin(user.getIsAdmin());
        }
        if (ObjectUtil.notEqual(existUser.getInviteCycleEnable(), user.getInviteCycleEnable())) {
            existUser.setInviteCycleEnable(user.getInviteCycleEnable());
        }
        if (ObjectUtil.notEqual(existUser.getEnable(), user.getEnable())) {
            existUser.setEnable(user.getEnable());
        }
        if (userService.updateById(existUser)) {
            redisService.del("panel::user::" + existUser.getId());
            return Result.ok().message("修改成功").messageEnglish("Update successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result deleteUserById(Integer id) {
        if (userService.removeById(id)) {
            redisService.deleteByKeys("panel::user::*");
            return Result.ok().message("删除成功").messageEnglish("Delete Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public Result getPlan(HttpServletRequest request) {
        return planService.getPlan(Integer.parseInt(request.getParameter("pageNo")), Integer.parseInt(request.getParameter("pageSize")));
    }

    @Override
    @Transactional
    public Result addPlan(Plan plan) {
        plan.setTransferEnable(FlowSizeConverterUtil.GbToBytes(plan.getTransferEnableGb()));
        plan.setPackagee(FlowSizeConverterUtil.GbToBytes(plan.getPackageGb()));
        if (planService.save(plan)) {
            redisService.deleteByKeys("panel::plan::*");
            return Result.ok().message("新增成功").messageEnglish("Add Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result updatePlanById(Plan plan) {
        plan.setTransferEnable(FlowSizeConverterUtil.GbToBytes(plan.getTransferEnableGb()));
        plan.setPackagee(FlowSizeConverterUtil.GbToBytes(plan.getPackageGb()));
        if (planService.updateById(plan)) {
            redisService.deleteByKeys("panel::plan::*");
            return Result.ok().message("修改成功").messageEnglish("Update Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result deletePlanById(Integer id) {
        if (planService.removeById(id)) {
            redisService.deleteByKeys("panel::plan::*");
            return Result.ok().message("删除成功").messageEnglish("Delete Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public Result getTutorial(HttpServletRequest request) {
        return tutorialService.getTutorial(Integer.parseInt(request.getParameter("pageNo")), Integer.parseInt(request.getParameter("pageSize")));
    }

    @Override
    @Transactional
    public Result addTutorial(Tutorial tutorial) {
        if (tutorialService.save(tutorial)) {
            redisService.deleteByKeys("panel::tutorial::*");
            return Result.ok().message("新增成功").messageEnglish("Add Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result updateTutorialById(Tutorial tutorial) {
        if (tutorialService.updateById(tutorial)) {
            redisService.deleteByKeys("panel::tutorial::*");
            return Result.ok().message("修改成功").messageEnglish("Update Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    @Transactional
    public Result deleteTutorialById(Integer id) {
        if (tutorialService.removeById(id)) {
            redisService.deleteByKeys("panel::tutorial::*");
            return Result.ok().message("删除成功").messageEnglish("Delete Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public Result getAnnouncement() {
        return userService.getAnnouncement();
    }

    @Override
    @Transactional
    public Result saveOrUpdateAnnouncement(Announcement announcement) {
        announcement.setTime(new Date());
        Boolean saveFlag = false;
        if (announcement.getSave()) {
            saveFlag = announcementService.saveOrUpdate(announcement);
            if (saveFlag) {
                redisService.set("panel::site::announcement", announcement);
            }
        }
        if (announcement.getBot()) {
            // TODO 发送bot提醒
        }
        if (announcement.getMail()) {
            // TODO 发送email提醒,从参数中拿html
        }
        if (saveFlag) {
            return Result.ok().message("操作成功").messageEnglish("Successfully");
        } else {
            return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public Result getOrder(HttpServletRequest request) {
        return orderService.getOrder(request);
    }

    @Override
    public Result getOrderByOrderId(String orderId) {
        Order order = orderService.getOrderByOrderId(orderId);
        if (ObjectUtil.isNotEmpty(order)) {
            return Result.ok().data("order", order);
        } else {
            return Result.error().message("无效订单ID").messageEnglish("Invalid Order ID");
        }
    }

    @Override
    public Result getCommission(Integer pageNo, Integer pageSize) {
        Map<String, Object> map = fundsService.getCommission(pageNo, pageSize);
        return Result.ok().data("data", map);
    }

    @Override
    public Result getWithdraw(Integer pageNo, Integer pageSize) {
        Map<String, Object> map = withdrawService.getWithdraw(pageNo, pageSize);
        return Result.ok().data("data", map);
    }

    @Override
    @Transactional
    public Result ackWithdrawById(Integer id) {
        // 修改status
        UpdateWrapper<Withdraw> withdrawUpdateWrapper = new UpdateWrapper<>();
        withdrawUpdateWrapper.set("status", 1).eq("status", 0).eq("id", id);
        if (withdrawService.update(withdrawUpdateWrapper)) {
            Withdraw withdraw = withdrawService.getById(id);
            // 给该用户生成一笔资金明细
            Funds funds = new Funds();
            funds.setUserId(withdraw.getUserId());
            funds.setTime(new Date());
            funds.setPrice(BigDecimal.ZERO.subtract(withdraw.getAmount()));
            funds.setContent("提现");
            funds.setContentEnglish("Withdrawal");
            funds.setRelatedOrderId(withdraw.getId().toString());
            if (fundsService.save(funds)) {
                return Result.ok().message("操作成功").messageEnglish("Successfully");
            }
        }
        return Result.setResult(ResultCodeEnum.UNKNOWN_ERROR);
    }

    @Override
    @Transactional
    public Result addScheduleTask(Schedule schedule) {
        if (!scheduleService.save(schedule)) {
            return Result.error().message("新增失败").messageEnglish("Failed");
        } else {
            if (schedule.getJobStatus() == 1) {
                SchedulingRunnable task = new SchedulingRunnable(schedule.getBeanName(), schedule.getMethodName(), schedule.getMethodParams());
                cronTaskRegistrar.addCronTask(task, schedule.getCronExpression());
            }
        }
        return Result.ok();
    }

    @Override
    @Transactional
    public Result updateScheduleTask(Schedule schedule) {
        // 保存原来的任务
        Schedule existedSchedule = scheduleService.getById(schedule.getId());
        // 更新新任务
        if (!scheduleService.updateById(schedule)) {
            return Result.error().message("修改失败").messageEnglish("Failed");
        } else {
            // 如果原来的任务status=1,停止原来的任务
            if (existedSchedule.getJobStatus() == 1) {
                SchedulingRunnable task = new SchedulingRunnable(existedSchedule.getBeanName(), existedSchedule.getMethodName(), existedSchedule.getMethodParams());
                cronTaskRegistrar.removeCronTask(task);
            }

            //添加新任务
            if (schedule.getJobStatus() == 1) {
                SchedulingRunnable task = new SchedulingRunnable(schedule.getBeanName(), schedule.getMethodName(), schedule.getMethodParams());
                cronTaskRegistrar.addCronTask(task, schedule.getCronExpression());
            }
        }
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteScheduleTask(Integer id) {
        // 保存原来的任务
        Schedule existedSchedule = scheduleService.getById(id);
        // 删除任务
        if (!scheduleService.removeById(id)) {
            return Result.error().message("删除失败").messageEnglish("Failed");
        } else {
            // 移除任务
            if (existedSchedule.getJobStatus() == 1) {
                SchedulingRunnable task = new SchedulingRunnable(existedSchedule.getBeanName(), existedSchedule.getMethodName(), existedSchedule.getMethodParams());
                cronTaskRegistrar.removeCronTask(task);
            }
        }
        return Result.ok();
    }

    @Override
    @Transactional
    public Result toggleScheduleTask(Schedule schedule) {
        // 保存原来的任务
        Schedule existedSchedule = scheduleService.getById(schedule.getId());

        // 已启动的关闭
        if (existedSchedule.getJobStatus() == 1) {
            // 修改状态
            existedSchedule.setJobStatus(0);
            if (scheduleService.updateById(existedSchedule)) {
                SchedulingRunnable task = new SchedulingRunnable(existedSchedule.getBeanName(), existedSchedule.getMethodName(), existedSchedule.getMethodParams());
                cronTaskRegistrar.removeCronTask(task);
                return Result.ok().message("定时任务已关闭").messageEnglish("Close successfully");
            } else {
                return Result.error().message("切换定时任务状态出现错误").messageEnglish("Toggle failed");
            }
            // 未启动的启动
        } else if (existedSchedule.getJobStatus() == 0) {
            // 修改状态
            existedSchedule.setJobStatus(1);
            if (scheduleService.updateById(existedSchedule)) {
                SchedulingRunnable task = new SchedulingRunnable((existedSchedule.getBeanName()), existedSchedule.getMethodName(), existedSchedule.getMethodParams());
                cronTaskRegistrar.addCronTask(task, existedSchedule.getCronExpression());
                return Result.ok().message("定时任务已启动").messageEnglish("Start successfully");
            } else {
                return Result.error().message("切换定时任务状态出现错误").messageEnglish("Toggle failed");
            }
        }
        return Result.error().message("切换定时任务状态出现错误").messageEnglish("Toggle failed");
    }
}