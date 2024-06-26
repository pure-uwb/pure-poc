/*
 * Copyright (C) 2013 MILLAU Julien
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.devnied.emvnfccard.enums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import at.zweng.emv.utils.EmvParsingException;
import fr.devnied.bitlib.BytesUtils;

/**
 * Enum which define all EMV apdu
 *
 * @author MILLAU Julien
 */
public enum CommandEnum {

    /**
     * Select command
     */
    SELECT(0x00, 0xA4, 0x04, 0x00),

    /**
     * Read record command
     */
    READ_RECORD(0x00, 0xB2, 0x00, 0x00),

    /**
     * GPO Command
     */
    GPO(0x80, 0xA8, 0x00, 0x00),

    /**
     * GPO Command
     */
    GET_DATA(0x80, 0xCA, 0x00, 0x00),

    GEN_AC(0x80, 0xAE, 0x00, 0x00),

    // Custom

    EXT_CL_HELLO(0x80, 0xD0, 0x00, 0x00),
    EXT_SIGN(0x80, 0xD1, 0x00, 0x00),
    EXT_SELECT_AID(0x80, 0xD2, 0x00, 0x00);


    /**
     * Class byte
     */
    private final int cla;

    /**
     * Instruction byte
     */
    private final int ins;

    /**
     * Parameter 1 byte
     */
    private final int p1;

    /**
     * Parameter 2 byte
     */
    private final int p2;

    /**
     * Constructor using field
     *
     * @param cla class
     * @param ins instruction
     * @param p1  parameter 1
     * @param p2  parameter 2
     */
    CommandEnum(final int cla, final int ins, final int p1, final int p2) {
        this.cla = cla;
        this.ins = ins;
        this.p1 = p1;
        this.p2 = p2;
    }

    private static final Map<String, CommandEnum> lookup = new HashMap<>();
    private static final Set<CommandEnum> extensionCommands = new HashSet<>();

    static {
        for (CommandEnum d : CommandEnum.values()) {
            lookup.put(
                    BytesUtils.bytesToStringNoSpace(new byte[]{(byte) d.getCla(), (byte) d.getIns(), (byte) d.getP1(), (byte) d.getP2()}), d);
        }

        extensionCommands.add(EXT_CL_HELLO);
        extensionCommands.add(EXT_SIGN);
        extensionCommands.add(EXT_SELECT_AID);
    }

    public static CommandEnum getEnum(byte[] cmd) {
        return lookup.get(BytesUtils.bytesToStringNoSpace(cmd));
    }

    public static boolean isExtensionCommand(byte[] cmd) throws EmvParsingException {
        if (cmd == null || cmd.length < 4) {
            throw new EmvParsingException("Command should contain at least 4 byes");
        }
        CommandEnum d = getEnum((Arrays.copyOfRange(cmd, 0, 4)));
        return extensionCommands.contains(d);
    }

    /**
     * Method used to get the field cla
     *
     * @return the cla
     */
    public int getCla() {
        return cla;
    }

    /**
     * Method used to get the field ins
     *
     * @return the ins
     */
    public int getIns() {
        return ins;
    }

    /**
     * Method used to get the field p1
     *
     * @return the p1
     */
    public int getP1() {
        return p1;
    }

    /**
     * Method used to get the field p2
     *
     * @return the p2
     */
    public int getP2() {
        return p2;
    }

}
