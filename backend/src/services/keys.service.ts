import { prisma } from '../db/prisma';

export interface WrappedKeyInput {
  pass_wrapped_mk: string; // base64
  pass_salt: string; // base64
  kdf_params: Record<string, unknown>;
  recovery_wrapped_mk: string; // base64
  kcv?: string; // base64
  current_key_version?: number;
}

export class KeysService {
  /**
   * Retrieves the latest wrapped key plus older key versions for a user.
   * Never logs key material.
   */
  async getKeys(userId: string) {
    const keys = await prisma.wrappedKeys.findMany({
      where: { userId },
      orderBy: { keyVersion: 'desc' },
    });

    if (keys.length === 0) {
      return { exists: false };
    }

    const latest = keys[0];
    const olderVersions = keys.slice(1).map((k) => k.keyVersion);

    return {
      exists: true,
      latest: {
        key_version: latest.keyVersion,
        pass_wrapped_mk: Buffer.from(latest.passWrappedMk).toString('base64'),
        pass_salt: Buffer.from(latest.passSalt).toString('base64'),
        kdf_params: latest.kdfParams,
        recovery_wrapped_mk: Buffer.from(latest.recoveryWrappedMk).toString('base64'),
        kcv: latest.kcv ? Buffer.from(latest.kcv).toString('base64') : null,
        updated_at: latest.updatedAt.toISOString(),
      },
      older_key_versions: olderVersions,
    };
  }

  /**
   * Put keys with create-only and version-guarded rotation semantics.
   * If keys exist, current_key_version must match the latest version.
   * Mismatch or missing version throws a 409 conflict.
   * Old versions are never deleted.
   */
  async putKeys(userId: string, input: WrappedKeyInput) {
    return prisma.$transaction(async (tx) => {
      const existingLatest = await tx.wrappedKeys.findFirst({
        where: { userId },
        orderBy: { keyVersion: 'desc' },
      });

      if (existingLatest) {
        if (
          input.current_key_version === undefined ||
          input.current_key_version === null ||
          input.current_key_version !== existingLatest.keyVersion
        ) {
          const err = new Error(
            `Key conflict: current_key_version (${input.current_key_version}) does not match latest (${existingLatest.keyVersion})`
          );
          (err as any).statusCode = 409;
          throw err;
        }

        const nextVersion = existingLatest.keyVersion + 1;
        const created = await tx.wrappedKeys.create({
          data: {
            userId,
            keyVersion: nextVersion,
            passWrappedMk: Buffer.from(input.pass_wrapped_mk, 'base64'),
            passSalt: Buffer.from(input.pass_salt, 'base64'),
            kdfParams: input.kdf_params as any,
            recoveryWrappedMk: Buffer.from(input.recovery_wrapped_mk, 'base64'),
            kcv: input.kcv ? Buffer.from(input.kcv, 'base64') : null,
          },
        });

        return {
          key_version: created.keyVersion,
          status: 'updated',
        };
      }

      // First time key creation: key_version = 1
      const created = await tx.wrappedKeys.create({
        data: {
          userId,
          keyVersion: 1,
          passWrappedMk: Buffer.from(input.pass_wrapped_mk, 'base64'),
          passSalt: Buffer.from(input.pass_salt, 'base64'),
          kdfParams: input.kdf_params as any,
          recoveryWrappedMk: Buffer.from(input.recovery_wrapped_mk, 'base64'),
          kcv: input.kcv ? Buffer.from(input.kcv, 'base64') : null,
        },
      });

      return {
        key_version: created.keyVersion,
        status: 'created',
      };
    });
  }
}
