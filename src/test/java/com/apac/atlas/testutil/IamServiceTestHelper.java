package com.apac.atlas.testutil;

import com.apac.atlas.core.storage.InMemoryStorage;
import com.apac.atlas.core.storage.StorageBackend;
import com.apac.atlas.services.iam.IamService;
import com.apac.atlas.services.iam.model.AccessKey;

import java.lang.reflect.Constructor;

public final class IamServiceTestHelper {

    private IamServiceTestHelper() {
    }

    @SuppressWarnings("unchecked")
    public static IamService iamServiceWithAccessKey(String accessKeyId, String secretAccessKey) {
        try {
            Constructor<IamService> constructor = IamService.class.getDeclaredConstructor(
                    StorageBackend.class,
                    StorageBackend.class,
                    StorageBackend.class,
                    StorageBackend.class,
                    StorageBackend.class,
                    StorageBackend.class,
                    StorageBackend.class,
                    String.class
            );
            constructor.setAccessible(true);

            InMemoryStorage<String, AccessKey> accessKeys = new InMemoryStorage<>();
            accessKeys.put(accessKeyId, new AccessKey(accessKeyId, secretAccessKey, "test-user"));

            return constructor.newInstance(
                    null,
                    null,
                    null,
                    null,
                    accessKeys,
                    null,
                    null,
                    "123456789012"
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to construct IamService test fixture", e);
        }
    }
}
