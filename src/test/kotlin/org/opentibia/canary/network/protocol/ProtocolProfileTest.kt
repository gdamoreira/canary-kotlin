package org.opentibia.canary.network.protocol

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript of Canary `tests/unit/server/network/protocol/multiprotocol_test.cpp`
 * (`ProtocolProfileRegistryTest`) plus feature/transport coverage for every
 * registered profile.
 *
 * C++ sources: src/server/network/protocol/protocol_profile.hpp/.cpp
 */
class ProtocolProfileTest {

    private val canary860Assets = ClientAssetSignatures(
        dat = 0x44363843u,
        spr = 0x53363843u,
        pic = 0x50363843u,
    )

    private val development860Assets = ClientAssetSignatures(
        dat = 0x4C2C7993u,
        spr = 0x4C220594u,
        pic = 0x4AE5C3D3u,
    )

    private fun featuresOf(id: ProtocolProfileId): List<ProtocolFeature> {
        val profile = ProtocolProfileRegistry.getProfile(id)
        assertThat(profile).isNotNull
        return ProtocolFeature.entries.filter { profile!!.hasFeature(it) }
    }

    @Test
    fun `version 860 profiles are different profiles`() {
        val vanilla = ProtocolProfileRegistry.resolveByClientVersion(860, ClientWireFamily.CipsoftVanilla)
        val shippedExtendedAssets = ProtocolProfileRegistry.resolveByClientVersionAndAssets(
            860,
            development860Assets,
            ClientWireFamily.CipsoftVanilla,
        )
        val canaryAssets = ProtocolProfileRegistry.resolveByClientVersionAndAssets(
            860,
            canary860Assets,
            ClientWireFamily.CipsoftVanilla,
        )
        val extendedAssets = ProtocolProfileRegistry.getProfile(ProtocolProfileId.Cipsoft860ExtendedAssets)
        val otcv8 = ProtocolProfileRegistry.resolveByClientVersion(860, ClientWireFamily.OTCv8Extended)

        assertThat(vanilla).isNotNull
        assertThat(shippedExtendedAssets).isNotNull
        assertThat(canaryAssets).isNotNull
        assertThat(extendedAssets).isNotNull
        assertThat(otcv8).isNotNull
        assertThat(vanilla!!.id).isNotEqualTo(otcv8!!.id)
        assertThat(vanilla.id).isNotEqualTo(extendedAssets!!.id)
        assertThat(extendedAssets.id).isNotEqualTo(otcv8.id)
        assertThat(vanilla.id).isEqualTo(ProtocolProfileId.Cipsoft860Vanilla)
        assertThat(shippedExtendedAssets!!.id).isEqualTo(ProtocolProfileId.Cipsoft860CanaryExtended)
        assertThat(canaryAssets!!.id).isEqualTo(ProtocolProfileId.Cipsoft860CanaryExtended)
        assertThat(extendedAssets.id).isEqualTo(ProtocolProfileId.Cipsoft860ExtendedAssets)
        assertThat(otcv8.id).isEqualTo(ProtocolProfileId.OTCv8Extended860)
        assertThat(vanilla.wireFamily).isEqualTo(ClientWireFamily.CipsoftVanilla)
        assertThat(shippedExtendedAssets.wireFamily).isEqualTo(ClientWireFamily.CipsoftVanilla)
        assertThat(extendedAssets.wireFamily).isEqualTo(ClientWireFamily.CipsoftVanilla)
        assertThat(otcv8.wireFamily).isEqualTo(ClientWireFamily.OTCv8Extended)
        assertThat(vanilla.rsaKeyFamily).isEqualTo(RSAKeyFamily.OpenTibia)
        assertThat(vanilla.clientVersion).isEqualTo(860)
        assertThat(shippedExtendedAssets.clientVersion).isEqualTo(860)
        assertThat(extendedAssets.clientVersion).isEqualTo(860)
        assertThat(otcv8.clientVersion).isEqualTo(860)
        assertThat(vanilla.hasFeature(ProtocolFeature.InlineLoginBugReportFlag)).isTrue()
        assertThat(vanilla.hasFeature(ProtocolFeature.RequiresItemMapper)).isTrue()
        assertThat(vanilla.hasFeature(ProtocolFeature.ExpertPvpModeByte)).isFalse()
        assertThat(shippedExtendedAssets.hasFeature(ProtocolFeature.ExtendedSpriteFiles)).isTrue()
        assertThat(shippedExtendedAssets.hasFeature(ProtocolFeature.MagicEffectU16)).isTrue()
        assertThat(shippedExtendedAssets.hasFeature(ProtocolFeature.RequiresItemMapper)).isFalse()
        assertThat(shippedExtendedAssets.hasFeature(ProtocolFeature.ExpertPvpModeByte)).isFalse()
        assertThat(extendedAssets.hasFeature(ProtocolFeature.InlineLoginBugReportFlag)).isTrue()
        assertThat(extendedAssets.hasFeature(ProtocolFeature.ExtendedSpriteFiles)).isTrue()
        assertThat(extendedAssets.hasFeature(ProtocolFeature.MagicEffectU16)).isFalse()
        assertThat(extendedAssets.hasFeature(ProtocolFeature.RequiresItemMapper)).isFalse()
        assertThat(extendedAssets.hasFeature(ProtocolFeature.ExpertPvpModeByte)).isFalse()
        assertThat(otcv8.hasFeature(ProtocolFeature.InlineLoginBugReportFlag)).isTrue()
        assertThat(otcv8.hasFeature(ProtocolFeature.ExpertPvpModeByte)).isFalse()
        assertThat(ProtocolProfileRegistry.isProfileAllowed(vanilla.id)).isTrue()
        assertThat(ProtocolProfileRegistry.isProfileAllowed(shippedExtendedAssets.id)).isTrue()
        assertThat(ProtocolProfileRegistry.isProfileAllowed(extendedAssets.id)).isTrue()
        assertThat(ProtocolProfileRegistry.isProfileAllowed(otcv8.id)).isFalse()
    }

    @Test
    fun `cipsoft 860 uses classic login layouts`() {
        val accountLayout = ProtocolProfileRegistry.resolveAccountLoginLayout(860)
        val gameLayout = ProtocolProfileRegistry.resolveGameLoginLayout(860)
        val profile = ProtocolProfileRegistry.getProfile(ProtocolProfileId.Cipsoft860Vanilla)

        assertThat(accountLayout).isNotNull
        assertThat(gameLayout).isNotNull
        assertThat(profile).isNotNull
        assertThat(accountLayout!!.responseTransport).isEqualTo(TransportProfileId.LegacyClassic)
        assertThat(accountLayout.bytesToSkipBeforeRsa).isEqualTo(12)
        assertThat(accountLayout.hasAssetSignaturesBeforeRsa).isTrue()
        assertThat(accountLayout.characterListLayout).isEqualTo(AccountCharacterListLayout.LegacyCharacterList)
        assertThat(accountLayout.sendsSessionKey).isFalse()
        assertThat(gameLayout!!.hasClientVersionU32).isFalse()
        assertThat(gameLayout.hasContentRevisionU16).isFalse()
        assertThat(gameLayout.hasPreviewState).isFalse()
        assertThat(gameLayout.authenticationLayout).isEqualTo(GameLoginAuthenticationLayout.AccountPassword)
        assertThat(profile!!.initialBehavior.transport).isEqualTo(TransportProfileId.LegacyClassic)
        assertThat(profile.initialBehavior.challenge.layout).isEqualTo(ChallengeLayout.Cipsoft860LoginChallenge)

        val extendedProfile = ProtocolProfileRegistry.getProfile(ProtocolProfileId.Cipsoft860ExtendedAssets)
        val extendedAccountLayout =
            ProtocolProfileRegistry.resolveAccountLoginLayout(ProtocolProfileId.Cipsoft860ExtendedAssets)
        val extendedGameLayout =
            ProtocolProfileRegistry.resolveGameLoginLayout(ProtocolProfileId.Cipsoft860ExtendedAssets)
        val canaryProfile = ProtocolProfileRegistry.getProfile(ProtocolProfileId.Cipsoft860CanaryExtended)
        val canaryAccountLayout =
            ProtocolProfileRegistry.resolveAccountLoginLayout(ProtocolProfileId.Cipsoft860CanaryExtended)
        val canaryGameLayout =
            ProtocolProfileRegistry.resolveGameLoginLayout(ProtocolProfileId.Cipsoft860CanaryExtended)

        assertThat(extendedProfile).isNotNull
        assertThat(extendedAccountLayout).isNotNull
        assertThat(extendedGameLayout).isNotNull
        assertThat(canaryProfile).isNotNull
        assertThat(canaryAccountLayout).isNotNull
        assertThat(canaryGameLayout).isNotNull
        assertThat(profile.initialBehavior.hasSameWireBehavior(extendedProfile!!.initialBehavior)).isTrue()
        assertThat(profile.initialBehavior.hasSameWireBehavior(canaryProfile!!.initialBehavior)).isTrue()
        assertThat(extendedAccountLayout!!.characterListLayout)
            .isEqualTo(AccountCharacterListLayout.LegacyCharacterList)
        assertThat(canaryAccountLayout!!.characterListLayout)
            .isEqualTo(AccountCharacterListLayout.LegacyCharacterList)
        assertThat(extendedGameLayout!!.authenticationLayout)
            .isEqualTo(GameLoginAuthenticationLayout.AccountPassword)
        assertThat(canaryGameLayout!!.authenticationLayout)
            .isEqualTo(GameLoginAuthenticationLayout.AccountPassword)
    }

    @Test
    fun `current and 1100 use different initial wire behavior`() {
        val current = ProtocolProfileRegistry.getCurrentProfile()
        val tibia1100 = ProtocolProfileRegistry.getProfile(ProtocolProfileId.Tibia1100)

        assertThat(tibia1100).isNotNull
        assertThat(current.id).isNotEqualTo(tibia1100!!.id)
        assertThat(current.initialBehavior.hasSameWireBehavior(tibia1100.initialBehavior)).isFalse()
        assertThat(current.hasFeature(ProtocolFeature.ExpertPvpModeByte)).isFalse()
        assertThat(tibia1100.hasFeature(ProtocolFeature.ExpertPvpModeByte)).isTrue()
        assertThat(current.hasFeature(ProtocolFeature.TacticsWithoutFightMode)).isTrue()
        assertThat(tibia1100.hasFeature(ProtocolFeature.OldProtocolCompat)).isTrue()
        assertThat(tibia1100.hasFeature(ProtocolFeature.TacticsWithoutFightMode)).isFalse()
        assertThat(ProtocolProfileRegistry.isProfileAllowed(tibia1100.id)).isTrue()
    }

    @Test
    fun `known profiles expose the canary feature flags`() {
        assertThat(featuresOf(ProtocolProfileId.Current)).containsExactlyInAnyOrder(
            ProtocolFeature.CurrentPayload,
            ProtocolFeature.LoginSpeedFormula,
            ProtocolFeature.ModernLoginSideSystems,
            ProtocolFeature.ResourceBalancePackets,
            ProtocolFeature.CustomMonkPackets,
            ProtocolFeature.MarketPackets,
            ProtocolFeature.ImbuementWindow,
            ProtocolFeature.MemorialPackets,
            ProtocolFeature.PlayerDataLevelPercentU16,
            ProtocolFeature.GameEventPayload,
            ProtocolFeature.OfficialTaskboardPackets,
            ProtocolFeature.OfficialVocationSpecificPlayerData,
            ProtocolFeature.OfficialWeaponProficiencyPayload,
            ProtocolFeature.GraphicalEffectSourceByte,
            ProtocolFeature.OfficialSoulSealsPackets,
            ProtocolFeature.OfficialSkillWheelPayload,
            ProtocolFeature.TacticsWithoutFightMode,
        )
        assertThat(featuresOf(ProtocolProfileId.Tibia1100)).containsExactlyInAnyOrder(
            ProtocolFeature.OldProtocolCompat,
            ProtocolFeature.LegacyPayload,
            ProtocolFeature.LoginSpeedFormula,
            ProtocolFeature.MarketPackets,
            ProtocolFeature.ImbuementWindow,
            ProtocolFeature.ExpertPvpModeByte,
        )
        assertThat(featuresOf(ProtocolProfileId.Cipsoft860Vanilla)).containsExactlyInAnyOrder(
            ProtocolFeature.OldProtocolCompat,
            ProtocolFeature.LegacyPayload,
            ProtocolFeature.RequiresItemMapper,
            ProtocolFeature.InlineLoginBugReportFlag,
        )
        assertThat(featuresOf(ProtocolProfileId.Cipsoft860ExtendedAssets)).containsExactlyInAnyOrder(
            ProtocolFeature.OldProtocolCompat,
            ProtocolFeature.LegacyPayload,
            ProtocolFeature.ExtendedSpriteFiles,
            ProtocolFeature.InlineLoginBugReportFlag,
        )
        assertThat(featuresOf(ProtocolProfileId.Cipsoft860CanaryExtended)).containsExactlyInAnyOrder(
            ProtocolFeature.OldProtocolCompat,
            ProtocolFeature.LegacyPayload,
            ProtocolFeature.ExtendedSpriteFiles,
            ProtocolFeature.MagicEffectU16,
            ProtocolFeature.InlineLoginBugReportFlag,
        )
        assertThat(featuresOf(ProtocolProfileId.OTCv8Extended860)).containsExactlyInAnyOrder(
            ProtocolFeature.OldProtocolCompat,
            ProtocolFeature.LegacyPayload,
            ProtocolFeature.RequiresItemMapper,
            ProtocolFeature.InlineLoginBugReportFlag,
        )
    }

    @Test
    fun `transport profiles match the canary framing table`() {
        val raw = ProtocolProfileRegistry.getTransportProfile(TransportProfileId.RawClientFirst)
        val modern = ProtocolProfileRegistry.getTransportProfile(TransportProfileId.CurrentModern)
        val legacyHeader = ProtocolProfileRegistry.getTransportProfile(TransportProfileId.LegacyRawWithLoginHeader)
        val legacyClassic = ProtocolProfileRegistry.getTransportProfile(TransportProfileId.LegacyClassic)

        assertThat(raw).isEqualTo(
            TransportProfile(
                id = TransportProfileId.RawClientFirst,
                outerLength = OuterLengthEncoding.RawBodyLength,
                encryptedPayload = EncryptedPayloadLayout.None,
                inboundChecksum = ChecksumMethods.None,
                outboundChecksum = ChecksumMethods.None,
                compression = CompressionLayout.None,
                modernLengthExtraBytes = 0,
                serverFirstPacketHeaderBytes = 0,
                hasCryptoHeader = false,
                lengthIncludesChecksum = false,
                sequenceHighBitSignalsCompression = false,
            ),
        )
        assertThat(modern).isEqualTo(
            TransportProfile(
                id = TransportProfileId.CurrentModern,
                outerLength = OuterLengthEncoding.ModernBlockCount,
                encryptedPayload = EncryptedPayloadLayout.ModernPaddingByte,
                inboundChecksum = ChecksumMethods.Sequence,
                outboundChecksum = ChecksumMethods.Sequence,
                compression = CompressionLayout.Official,
                modernLengthExtraBytes = CHECKSUM_LENGTH,
                serverFirstPacketHeaderBytes = CHECKSUM_LENGTH + 2,
                hasCryptoHeader = true,
                lengthIncludesChecksum = true,
                sequenceHighBitSignalsCompression = true,
            ),
        )
        assertThat(legacyHeader).isEqualTo(
            TransportProfile(
                id = TransportProfileId.LegacyRawWithLoginHeader,
                outerLength = OuterLengthEncoding.RawBodyLength,
                encryptedPayload = EncryptedPayloadLayout.LegacyInnerLength,
                inboundChecksum = ChecksumMethods.Adler32,
                outboundChecksum = ChecksumMethods.Adler32,
                compression = CompressionLayout.None,
                modernLengthExtraBytes = 0,
                serverFirstPacketHeaderBytes = CHECKSUM_LENGTH + 1,
                hasCryptoHeader = true,
                lengthIncludesChecksum = true,
                sequenceHighBitSignalsCompression = false,
            ),
        )
        assertThat(legacyClassic).isEqualTo(
            legacyHeader.copy(id = TransportProfileId.LegacyClassic),
        )
    }

    @Test
    fun `current and tibia1100 login layouts match canary`() {
        val currentAccount = ProtocolProfileRegistry.resolveAccountLoginLayout(ProtocolProfileId.Current)
        val currentGame = ProtocolProfileRegistry.resolveGameLoginLayout(ProtocolProfileId.Current)
        val tibia1100Account = ProtocolProfileRegistry.resolveAccountLoginLayout(1100)
        val tibia1100Game = ProtocolProfileRegistry.resolveGameLoginLayout(1100)

        assertThat(currentAccount).isEqualTo(
            AccountLoginLayout(
                profileId = ProtocolProfileId.Current,
                clientVersion = CLIENT_VERSION,
                responseTransport = TransportProfileId.CurrentModern,
                bytesToSkipBeforeRsa = 17,
                hasAssetSignaturesBeforeRsa = false,
                characterListLayout = AccountCharacterListLayout.WorldListWithSessionKey,
                sendsSessionKey = true,
            ),
        )
        assertThat(currentGame).isEqualTo(
            GameLoginLayout(
                profileId = ProtocolProfileId.Current,
                clientVersion = CLIENT_VERSION,
                hasClientVersionU32 = true,
                hasClientVersionString = true,
                hasAssetHashString = true,
                hasContentRevisionU16 = false,
                hasPreviewState = true,
                authenticationLayout = GameLoginAuthenticationLayout.SessionKey,
                hasChallengeResponse = true,
                hasOtcV8Probe = true,
            ),
        )
        assertThat(tibia1100Account).isEqualTo(
            AccountLoginLayout(
                profileId = ProtocolProfileId.Tibia1100,
                clientVersion = 1100,
                responseTransport = TransportProfileId.LegacyClassic,
                bytesToSkipBeforeRsa = 17,
                hasAssetSignaturesBeforeRsa = false,
                characterListLayout = AccountCharacterListLayout.WorldListWithSessionKey,
                sendsSessionKey = true,
            ),
        )
        assertThat(tibia1100Game).isEqualTo(
            GameLoginLayout(
                profileId = ProtocolProfileId.Tibia1100,
                clientVersion = 1100,
                hasClientVersionU32 = true,
                hasClientVersionString = false,
                hasAssetHashString = false,
                hasContentRevisionU16 = true,
                hasPreviewState = true,
                authenticationLayout = GameLoginAuthenticationLayout.SessionKey,
                hasChallengeResponse = true,
                hasOtcV8Probe = true,
            ),
        )
    }

    @Test
    fun `allowed client protocol description lists enabled legacy labels`() {
        assertThat(ProtocolProfileRegistry.getAllowedClientProtocolDescription(false)).isEqualTo("15.25")
        assertThat(ProtocolProfileRegistry.getAllowedClientProtocolDescription(true)).isEqualTo("15.25, 10x and 8.6")
        assertThat(ProtocolProfileRegistry.getUnsupportedClientProtocolMessage(false))
            .isEqualTo("Only supported client protocol is 15.25.")
        assertThat(ProtocolProfileRegistry.getUnsupportedClientProtocolMessage(true))
            .isEqualTo("Only supported client protocols are 15.25, 10x and 8.6.")
    }

    @Test
    fun `shipped 860 asset signatures select the canary runtime profile`() {
        val extendedClientLibrary = ClientAssetSignatures(
            dat = 0x44545845u,
            spr = 0x44545845u,
            pic = 0x44545845u,
        )
        val extendedClientLibrarySprite = ClientAssetSignatures(
            dat = 0x4C2C7993u,
            spr = 0x44545845u,
            pic = 0x4AE5C3D3u,
        )
        val unknown = ClientAssetSignatures(dat = 1u, spr = 2u, pic = 3u)

        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(860, development860Assets)!!.id,
        ).isEqualTo(ProtocolProfileId.Cipsoft860CanaryExtended)
        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(860, canary860Assets)!!.id,
        ).isEqualTo(ProtocolProfileId.Cipsoft860CanaryExtended)
        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(860, extendedClientLibrary)!!.id,
        ).isEqualTo(ProtocolProfileId.Cipsoft860CanaryExtended)
        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(860, extendedClientLibrarySprite)!!.id,
        ).isEqualTo(ProtocolProfileId.Cipsoft860CanaryExtended)
        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(860, unknown)!!.id,
        ).isEqualTo(ProtocolProfileId.Cipsoft860Vanilla)
        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(
                860,
                canary860Assets,
                ClientWireFamily.OTCv8Extended,
            )!!.id,
        ).isEqualTo(ProtocolProfileId.OTCv8Extended860)
        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(1100, canary860Assets)!!.id,
        ).isEqualTo(ProtocolProfileId.Tibia1100)
        assertThat(
            ProtocolProfileRegistry.resolveByClientVersionAndAssets(CLIENT_VERSION, canary860Assets)!!.id,
        ).isEqualTo(ProtocolProfileId.Current)
    }

    @Test
    fun `unknown versions and disabled profiles resolve to no layout`() {
        assertThat(ProtocolProfileRegistry.resolveByClientVersion(999)).isNull()
        assertThat(ProtocolProfileRegistry.resolveByClientVersionAndAssets(999, canary860Assets)).isNull()
        assertThat(ProtocolProfileRegistry.resolveAccountLoginLayout(999)).isNull()
        assertThat(ProtocolProfileRegistry.resolveGameLoginLayout(999)).isNull()
        assertThat(
            ProtocolProfileRegistry.resolveAccountLoginLayout(ProtocolProfileId.OTCv8Extended860),
        ).isNull()
        assertThat(
            ProtocolProfileRegistry.resolveGameLoginLayout(ProtocolProfileId.OTCv8Extended860),
        ).isNull()
    }

    @Test
    fun `default modern initial behavior matches the current profile`() {
        val behavior = ProtocolProfileRegistry.defaultModernInitialBehavior()

        assertThat(behavior.transport).isEqualTo(TransportProfileId.CurrentModern)
        assertThat(behavior.challenge.flow).isEqualTo(GameHandshakeFlow.ServerChallengeBeforeLogin)
        assertThat(behavior.challenge.layout).isEqualTo(ChallengeLayout.CurrentLoginChallenge)
        assertThat(behavior.expectedProfile).isEqualTo(ProtocolProfileId.Current)
        assertThat(ProtocolProfileRegistry.getCurrentProfile().initialBehavior.hasSameWireBehavior(behavior))
            .isTrue()
    }
}
