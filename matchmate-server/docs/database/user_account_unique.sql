-- Remove duplicate accounts before applying this constraint.
ALTER TABLE `user`
    ADD CONSTRAINT `uk_user_account` UNIQUE (`userAccount`);
