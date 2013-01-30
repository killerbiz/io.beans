package io.beans.sizeof;



public enum StaticFieldPolicy {

    NONE {
        @Override
        FieldFilter filterStatics(Environment env) {
            return env.onlyGloballyAnnotated;
        }
    },
    DIRECT_REFERENCES,
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
