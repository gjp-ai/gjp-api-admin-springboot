package org.ganjp.api.auth.role;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for UserRole entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleId implements Serializable {
    
    private String user;
    private String role;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleId that = (UserRoleId) o;
        return Objects.equals(user, that.user) && Objects.equals(role, that.role);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(user, role);
    }
}
