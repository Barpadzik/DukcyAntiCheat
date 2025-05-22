package pl.barpad.duckyanticheat.model;

import java.util.Objects;

public class ViolationKey {
    private final String playerName;
    private final String checkType;

    public ViolationKey(String playerName, String checkType) {
        this.playerName = playerName;
        this.checkType = checkType;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCheckType() {
        return checkType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ViolationKey that = (ViolationKey) o;
        return Objects.equals(playerName, that.playerName) &&
                Objects.equals(checkType, that.checkType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerName, checkType);
    }

    @Override
    public String toString() {
        return playerName + ":" + checkType;
    }
}