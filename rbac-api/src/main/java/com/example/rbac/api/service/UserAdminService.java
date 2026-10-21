package com.example.rbac.api.service;

import com.example.rbac.api.dto.PageResult;
import com.example.rbac.api.dto.user.AssignRolesCommand;
import com.example.rbac.api.dto.user.ChangeUserStatusCommand;
import com.example.rbac.api.dto.user.CreateUserCommand;
import com.example.rbac.api.dto.user.ResetPasswordCommand;
import com.example.rbac.api.dto.user.UpdateUserCommand;
import com.example.rbac.api.dto.user.UserListItem;
import com.example.rbac.api.dto.user.UserProfile;
import com.example.rbac.api.dto.user.UserQuery;
import com.example.rbac.api.dto.user.UserRoles;

import java.util.Optional;

/**
 * User administration contract implemented by {@code rbac-core}.
 * Write methods are transactional and emit CHANGE audit records.
 */
public interface UserAdminService {

    PageResult<UserListItem> list(UserQuery query);

    Optional<UserProfile> findProfile(long userId);

    long create(CreateUserCommand command);

    void update(UpdateUserCommand command);

    void delete(long operatorId, long userId);

    void changeStatus(ChangeUserStatusCommand command);

    void resetPassword(ResetPasswordCommand command);

    Optional<UserRoles> findRoles(long userId);

    void assignRoles(AssignRolesCommand command);
}
