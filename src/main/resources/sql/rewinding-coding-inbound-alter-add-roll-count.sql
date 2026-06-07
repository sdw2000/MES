ALTER TABLE `rewinding_coding_inbound`
  ADD COLUMN `rewinding_roll_count` INT NOT NULL DEFAULT 1 COMMENT '复卷卷数' AFTER `rewinding_length_m`;
