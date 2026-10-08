package com.anyujia.anyujiaadminservice.config.service;

import java.util.List;

import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentAddReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentEditReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.vo.ArgumentVO;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;

/**
 * 系统参数服务接口。
 */
public interface ISysArgumentService {

    /**
     * 新增参数。
     *
     * @param argumentAddReqDTO 参数新增请求
     * @return 参数主键 ID
     */
    Long add(ArgumentAddReqDTO argumentAddReqDTO);

    /**
     * 分页查询参数列表。
     *
     * @param argumentListReqDTO 参数分页查询请求
     * @return 参数分页结果
     */
    BasePageVO<ArgumentVO> list(ArgumentListReqDTO argumentListReqDTO);

    /**
     * 编辑参数。
     *
     * @param argumentEditReqDTO 参数编辑请求
     * @return 参数主键 ID
     */
    Long edit(ArgumentEditReqDTO argumentEditReqDTO);

    /**
     * 根据参数业务键查询参数。
     *
     * @param configKey 参数业务键
     * @return 参数信息，不存在时返回 {@code null}
     */
    ArgumentDTO getByConfigKey(String configKey);

    /**
     * 根据多个参数业务键批量查询参数。
     *
     * @param configKeys 参数业务键列表
     * @return 参数列表
     */
    List<ArgumentDTO> getByConfigKeys(List<String> configKeys);
}
