package com.anyujia.anyujiaadminapi.config.feign;

import java.util.List;

import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentAddReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentEditReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.vo.ArgumentVO;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 参数服务 Feign 客户端。
 */
@FeignClient(contextId = "argumentFeignClient", value = "anyujia-admin")
public interface ArgumentFeignClient {

    /**
     * 新增参数。
     *
     * @param argumentAddReqDTO 参数新增请求
     * @return 参数主键 ID
     */
    @PostMapping("/argument/add")
    ApiResult<Long> add(@RequestBody ArgumentAddReqDTO argumentAddReqDTO);

    /**
     * 分页查询参数列表。
     *
     * @param argumentListReqDTO 参数分页查询请求
     * @return 参数分页结果
     */
    @GetMapping("/argument/list")
    ApiResult<BasePageVO<ArgumentVO>> list(@SpringQueryMap ArgumentListReqDTO argumentListReqDTO);

    /**
     * 编辑参数。
     *
     * @param argumentEditReqDTO 参数编辑请求
     * @return 参数主键 ID
     */
    @PostMapping("/argument/edit")
    ApiResult<Long> edit(@RequestBody ArgumentEditReqDTO argumentEditReqDTO);

    /**
     * 根据参数业务键查询参数。
     *
     * @param configKey 参数业务键
     * @return 参数信息
     */
    @GetMapping("/argument/key")
    ApiResult<ArgumentDTO> getByConfigKey(@RequestParam("configKey") String configKey);

    /**
     * 根据多个参数业务键批量查询参数。
     *
     * @param configKeys 参数业务键列表
     * @return 参数列表
     */
    @GetMapping("/argument/keys")
    ApiResult<List<ArgumentDTO>> getByConfigKeys(@RequestParam("configKeys") List<String> configKeys);
}
