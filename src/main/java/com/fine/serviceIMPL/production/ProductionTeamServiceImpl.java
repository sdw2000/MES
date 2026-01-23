package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.production.ProductionStaffMapper;
import com.fine.Dao.production.ProductionTeamMapper;
import com.fine.model.production.ProductionStaff;
import com.fine.model.production.ProductionTeam;
import com.fine.service.production.ProductionTeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 班组Service实现类
 */
@Service
public class ProductionTeamServiceImpl extends ServiceImpl<ProductionTeamMapper, ProductionTeam> 
        implements ProductionTeamService {

    @Autowired
    private ProductionTeamMapper teamMapper;

    @Autowired
    private ProductionStaffMapper staffMapper;

    @Override
    public Map<String, Object> getTeamList(String teamCode, String teamName, Long workshopId,
                                           Integer status, Integer page, Integer size) {
        IPage<ProductionTeam> pageRequest = new Page<>(page, size);
        IPage<ProductionTeam> pageResult = teamMapper.selectTeamPageList(pageRequest, teamCode, teamName, workshopId, status);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pageNum", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        return result;
    }

    @Override
    public IPage<ProductionTeam> getTeamPage(String teamCode, String teamName, Long workshopId,
                                             Integer status, Integer page, Integer size) {
        IPage<ProductionTeam> pageRequest = new Page<>(page, size);
        IPage<ProductionTeam> pageResult = teamMapper.selectTeamPageList(pageRequest, teamCode, teamName, workshopId, status);
        return pageResult;
    }

    @Override
    public ProductionTeam getTeamDetail(Long id) {
        ProductionTeam team = teamMapper.selectTeamById(id);
        if (team != null) {
            // 查询组员列表
            List<ProductionStaff> members = staffMapper.selectByTeamId(id);
            team.setMembers(members);
        }
        return team;
    }

    @Override
    @Transactional
    public boolean addTeam(ProductionTeam team) {
        // 检查班组编号是否重复
        int count = teamMapper.checkTeamCodeExists(team.getTeamCode(), 0L);
        if (count > 0) {
            throw new RuntimeException("班组编号已存在：" + team.getTeamCode());
        }
        
        team.setCreateTime(new Date());
        team.setUpdateTime(new Date());
        return teamMapper.insert(team) > 0;
    }

    @Override
    @Transactional
    public boolean updateTeam(ProductionTeam team) {
        // 检查班组编号是否重复
        int count = teamMapper.checkTeamCodeExists(team.getTeamCode(), team.getId());
        if (count > 0) {
            throw new RuntimeException("班组编号已存在：" + team.getTeamCode());
        }
        
        team.setUpdateTime(new Date());
        return teamMapper.updateById(team) > 0;
    }

    @Override
    @Transactional
    public boolean deleteTeam(Long id) {
        // 检查是否有组员
        List<ProductionStaff> members = staffMapper.selectByTeamId(id);
        if (members != null && !members.isEmpty()) {
            throw new RuntimeException("该班组下还有" + members.size() + "名组员，请先移除组员后再删除班组");
        }
        return teamMapper.deleteById(id) > 0;
    }

    @Override
    public List<ProductionTeam> getTeamsByWorkshop(Long workshopId) {
        return teamMapper.selectByWorkshopId(workshopId);
    }

    @Override
    public List<ProductionTeam> getAllActiveTeams() {
        return teamMapper.selectAllActive();
    }
}
