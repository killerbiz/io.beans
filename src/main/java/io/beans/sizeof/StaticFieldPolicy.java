package io.beans.sizeof;

/**
 * This defined how values in static fields of newly added classes
 * shall be handled by the Environment.
 * 
 * @author kuli
 */
public enum StaticFieldPolicy {

    /**
     * No static fields get analyzed except those that are annotated explicitely.
     */
    NOTHING {
        @Override
        FieldFilter filterStatics(Environment env) {
            return env.onlyGloballyAnnotated;
        }
    },

    /**
     * Only values of final static fields or explicitely annotated static fields
     * will be marked as global.
     */
    ONLY_CONSTANTS {
        @Override
        FieldFilter filterStatics(Environment env) {
            return env.onlyGloballyAnnotated;
        }
    },

    /**
     * All values that are in a static fields will be marked as global, but not
     * their referenced values.
     */
    DIRECT_REFERENCES,

    /**
     * All values of static fields and all indirectly referenced values will be marked
     * as global.
     */
    FULL_TREE {
        @Override
        FieldCallback addToGlobalsCallback(Environment env) {
            return env.deepGlobalSetter;
        }
    };

    FieldFilter filterStatics(Environment env) {
        return env.allowOnlyNonGlobal;
    }

    FieldCallback addToGlobalsCallback(Environment env) {
        return env.directGlobalSetter;
    }
}
