$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$rewindingContent = @"
package com.fine.Dao.production;

import com.fine.model.production.ScheduleRewinding;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScheduleRewindingMapper {
    @Select("SELECT sr.*, e.equipment_name FROM schedule_rewinding sr LEFT JOIN equipment e ON sr.equipment_id = e.id WHERE sr.schedule_id = #{scheduleId}")
    List<ScheduleRewinding> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    @Select("SELECT sr.*, e.equipment_name FROM schedule_rewinding sr LEFT JOIN equipment e ON sr.equipment_id = e.id WHERE sr.id = #{id}")
    ScheduleRewinding selectById(@Param("id") Long id);
    
    @Delete("DELETE FROM schedule_rewinding WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
}
"@

$slittingContent = @"
package com.fine.Dao.production;

import com.fine.model.production.ScheduleSlitting;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScheduleSlittingMapper {
    @Select("SELECT ss.*, e.equipment_name FROM schedule_slitting ss LEFT JOIN equipment e ON ss.equipment_id = e.id WHERE ss.schedule_id = #{scheduleId}")
    List<ScheduleSlitting> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    @Select("SELECT ss.*, e.equipment_name FROM schedule_slitting ss LEFT JOIN equipment e ON ss.equipment_id = e.id WHERE ss.id = #{id}")
    ScheduleSlitting selectById(@Param("id") Long id);
    
    @Delete("DELETE FROM schedule_slitting WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
}
"@

[System.IO.File]::WriteAllText("E:\java\MES\src\main\java\com\fine\Dao\production\ScheduleRewindingMapper.java", $rewindingContent, $utf8NoBom)
[System.IO.File]::WriteAllText("E:\java\MES\src\main\java\com\fine\Dao\production\ScheduleSlittingMapper.java", $slittingContent, $utf8NoBom)

Write-Host "Files created successfully"
