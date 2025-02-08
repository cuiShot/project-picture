package com.cc.ccPictureBackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cc.ccPictureBackend.exception.BusinessException;
import com.cc.ccPictureBackend.exception.ErrorCode;
import com.cc.ccPictureBackend.exception.ThrowUtils;
import com.cc.ccPictureBackend.mapper.SpaceMapper;
import com.cc.ccPictureBackend.model.dto.space.SpaceAddRequest;
import com.cc.ccPictureBackend.model.dto.space.SpaceQueryRequest;
import com.cc.ccPictureBackend.model.entity.Picture;
import com.cc.ccPictureBackend.model.entity.Space;
import com.cc.ccPictureBackend.model.entity.SpaceUser;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.model.enums.SpaceLevelEnum;
import com.cc.ccPictureBackend.model.enums.SpaceRoleEnum;
import com.cc.ccPictureBackend.model.enums.SpaceTypeEnum;
import com.cc.ccPictureBackend.model.vo.PictureVO;
import com.cc.ccPictureBackend.model.vo.SpaceVO;
import com.cc.ccPictureBackend.model.vo.UserVO;
import com.cc.ccPictureBackend.service.SpaceService;
import com.cc.ccPictureBackend.service.SpaceUserService;
import com.cc.ccPictureBackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author cuicui
 * @Description
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {


    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    /**
     * 用户创建空间
     * <p>
     * 填充默认参数
     * 校验参数
     * 校验权限，非管理员只能创建普通级别的空间（TODO 非会员）
     * 控制一个用户只能创建一个私有空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return 返回新建 Space 的 ID
     */
    @Override
    public Long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {

        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            spaceAddRequest.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            spaceAddRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (spaceAddRequest.getSpaceType() == null) {
            spaceAddRequest.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);

        // 填充数据,根据级别填充参数
        fillSpaceBySpaceLevel(space);
        // 校验数据
        validSpace(space, true);

        Long userId = loginUser.getId();
        space.setUserId(userId);

        // 权限校验
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创造指定级别的空间");
        }
        // 控制一个用户只能创建一个私有空间 对用户加锁
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                if (!userService.isAdmin(loginUser)) {
                    boolean exists = this.lambdaQuery()
                            .eq(Space::getUserId, userId)
                            .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                            .exists();
                    ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间仅能创建一个");
                }
                // 写入数据库
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                // 如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 返回新写入的数据 id
                return space.getId();
            });

            return Optional.ofNullable((newSpaceId)).orElse(-1L);
        }
    }


    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return spaceQueryWrapper;
        }

        // 获取查询请求对象的各个属性值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        Integer spaceType = spaceQueryRequest.getSpaceType();

        // 将获得的查询条件设置在 queryMapper中
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        spaceQueryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);

        // 排序
        spaceQueryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return spaceQueryWrapper;
    }

    /**
     * 获取VOPage
     *
     * @param spacePage
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSapceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        // 原始分页信息复制到 新分页
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        // 原始分页为空，返回空分页
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // pictureList 封装到 pictureVOList,使用stream流
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // pictureVOList 中的 pictureVO对象 关联用户信息
        // 这里不对 每个 Picture 中的userId进行获取，而是直接获取全部图片的 userId 得到userIdSet
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .collect(Collectors.toSet());
        // 1 => user1、2 => user2
        // 实际 每个id对应就一个对象,但是方法groupingBy只能返回 list
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 给 pictureVOList 中的每一个 pictureVO 对象设置 user
        spaceVOList.forEach(SpaceVO -> {
            Long userId = SpaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            SpaceVO.setUser(userService.getUserVO(user));

        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 获取封装类
     * @param space
     * @param request
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }


    /**
     * 校验空间数据 Space 数据，flag true代表 创建的时候校验
     * flag flase 代表编辑是的校验
     *
     * @param space
     * @param flag
     */
    @Override
    public void validSpace(Space space, boolean flag) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);

        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 创建: flag == true
        if (flag) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceLevel == null, ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            ThrowUtils.throwIf(spaceType == null, ErrorCode.PARAMS_ERROR,"空间类型不能为空");
        }
        // 修改数据
        else {
            if (spaceLevel != null && spaceLevelEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别错误，不存在");
            }
            if(spaceType != null && spaceTypeEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类型不存在");
            }
            if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
            }
        }
    }


    /**
     * 创建 Space 的时候，需要根据级别自动填充限额数据
     *
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        if (spaceLevelEnum != null) {
            long maxCount = spaceLevelEnum.getMaxCount();
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            if (space.getMaxCount() == null)
                space.setMaxCount(maxCount);
        }
    }


}
