package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.ProductionTeam;

import java.util.List;
import java.util.Map;

public interface ProductionTeamService extends IService<ProductionTeam> {
    
    Map<String, Object> getTeamList(String teamCode, String teamName, Long workshopId, Integer status, Integer page, Integer size);
    IPage<ProductionTeam> getTeamPage(String teamCode, String teamName, Long workshopId, Integer status, Integer page, Integer size);
    ProductionTeam getTeamDetail(Long id);
    boolean addTeam(ProductionTeam team);
    boolean updateTeam(ProductionTeam team);
    boolean deleteTeam(Long id);
    List<ProductionTeam> getTeamsByWorkshop(Long workshopId);
    List<ProductionTeam> getAllActiveTeams();
}
