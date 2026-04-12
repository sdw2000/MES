ALTER TABLE tape_raw_material
    ADD COLUMN performance_params TEXT NULL COMMENT '性能参数(JSON)' AFTER spec;
