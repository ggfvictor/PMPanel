package project.daihao18.panel.service;

import project.daihao18.panel.entity.SsNode;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @InterfaceName: SubService
 * @Description:
 * @Author: code18
 * @Date: 2020-11-23 14:05
 */
public interface SubService {
    String getSubs(String link, String type, HttpServletRequest request) throws IOException;

    List<SsNode> getEnableNodes(String link);

    List<Map<String, Object>> getSSList(String link);

    List<Map<String, Object>> getV2rayList(String link);
}