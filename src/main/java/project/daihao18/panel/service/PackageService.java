package project.daihao18.panel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import project.daihao18.panel.entity.Package;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @InterfaceName: PackageService
 * @Description:
 * @Author: code18
 * @Date: 2020-10-07 21:13
 */
public interface PackageService extends IService<Package> {
    void expiredFinishedPackageOrder();

    boolean updateFinishedPackageOrder(boolean isMixedPay, BigDecimal mixedMoneyAmount, BigDecimal mixedPayAmount, String payType, Date payTime, Integer status, Integer id);
}