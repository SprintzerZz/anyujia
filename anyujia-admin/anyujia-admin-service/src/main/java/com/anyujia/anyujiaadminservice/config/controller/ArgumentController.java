package com.anyujia.anyujiaadminservice.config.controller;

import java.util.List;

import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentAddReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentEditReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.ArgumentListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.vo.ArgumentVO;
import com.anyujia.anyujiaadminapi.config.feign.ArgumentFeignClient;
import com.anyujia.anyujiaadminservice.config.service.ISysArgumentService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参数服务相关接口。
 *
 * <p>实现 {@link ArgumentFeignClient}，保证 Controller 暴露路径和 Feign 调用路径一致。</p>
 */
@RestController
@RequiredArgsConstructor
public class ArgumentController implements ArgumentFeignClient {

    /**
     * 系统参数服务。
     */
    private final ISysArgumentService sysArgumentService;

    /**
     * 新增参数。
     *
     * @param argumentAddReqDTO 参数新增请求
     * @return 参数主键 ID
     */
    @Override
    public ApiResult<Long> add(@RequestBody @Validated ArgumentAddReqDTO argumentAddReqDTO) {
        return ApiResult.success(sysArgumentService.add(argumentAddReqDTO));
    }

    /**
     * 分页查询参数列表。
     *
     * @param argumentListReqDTO 参数分页查询请求
     * @return 参数分页结果
     */
    @Override
    public ApiResult<BasePageVO<ArgumentVO>> list(@Validated ArgumentListReqDTO argumentListReqDTO) {
        return ApiResult.success(sysArgumentService.list(argumentListReqDTO));
    }

    /**
     * 编辑参数。
     *
     * @param argumentEditReqDTO 参数编辑请求
     * @return 参数主键 ID
     */
    @Override
    public ApiResult<Long> edit(@RequestBody @Validated ArgumentEditReqDTO argumentEditReqDTO) {
        return ApiResult.success(sysArgumentService.edit(argumentEditReqDTO));
    }

    /**
     * 根据参数业务键查询参数。
     *
     * @param configKey 参数业务键
     * @return 参数信息
     */
    @Override
    public ApiResult<ArgumentDTO> getByConfigKey(String configKey) {
        return ApiResult.success(sysArgumentService.getByConfigKey(configKey));
    }

    /**
     * 根据多个参数业务键批量查询参数。
     *
     * @param configKeys 参数业务键列表
     * @return 参数列表
     */
    @Override
    public ApiResult<List<ArgumentDTO>> getByConfigKeys(List<String> configKeys) {
        return ApiResult.success(sysArgumentService.getByConfigKeys(configKeys));
    }
}
