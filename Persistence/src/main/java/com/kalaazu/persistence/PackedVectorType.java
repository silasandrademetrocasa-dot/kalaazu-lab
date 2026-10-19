package com.kalaazu.persistence;

import com.kalaazu.math.Vector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Persists a Vector as a packed BIGINT (upper 32 bits X, lower 32 bits Y).
 * This is only for columns using packed long coordinates, such as
 * kalaazu.accounts_ships.position.
 *
 * Other maps/portal/station Vector columns remain VARCHAR and must continue
 * using VectorType.
 */
public class PackedVectorType extends VectorType {
    @Override
    public int getSqlType() {
        return Types.BIGINT;
    }

    @Override
    public Vector nullSafeGet(ResultSet rs, int position,
                              SharedSessionContractImplementor session,
                              Object owner) throws SQLException {
        long packed = rs.getLong(position);
        if (rs.wasNull()) {
            return null;
        }
        return new Vector(packed);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Vector value, int index,
                            SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.BIGINT);
        } else {
            st.setLong(index, value.toLong());
        }
    }
}
