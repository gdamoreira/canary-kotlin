package org.opentibia.canary.network.protocol

/**
 * Kotlin port of Canary's protocol profile registry.
 *
 * C++ sources: `src/server/network/protocol/protocol_profile.hpp` and
 * `src/server/network/protocol/protocol_profile.cpp`. The profile constants are
 * registered with the exact values (versions, transports, features, asset
 * signatures and login layouts) declared by Canary.
 */

/** `CLIENT_VERSION` from `src/core.hpp`. */
const val CLIENT_VERSION = 1525

/** `CHECKSUM_LENGTH` from `src/server/server_definitions.hpp`. */
const val CHECKSUM_LENGTH = 4

enum class ProtocolProfileId {
    Current,
    Tibia1100,
    Cipsoft860Vanilla,
    Cipsoft860ExtendedAssets,
    Cipsoft860CanaryExtended,
    OTCv8Extended860,
}

enum class ClientWireFamily {
    CipsoftVanilla,
    OTClientCompatible,
    OTCv8Extended,
}

enum class RSAKeyFamily {
    OpenTibia,
    CipSoftOfficial,
}

enum class ProtocolSupportState {
    Enabled,
    BlockedPendingFixture,
}

enum class ItemMapperPolicy {
    NotRequired,
    RequiredBeforeWorldEnter,
    Optional,
}

/** `ChecksumMethods_t` from `src/server/server_definitions.hpp`. */
enum class ChecksumMethods {
    None,
    Adler32,
    Sequence,
}

/**
 * `ProtocolFeature` bitmask from `protocol_profile.hpp`. Each entry carries the
 * single bit that Canary assigns it; combine masks through [protocolFeatureMask].
 */
enum class ProtocolFeature(val mask: Long) {
    None(0L),
    CurrentPayload(1L shl 0),
    OldProtocolCompat(1L shl 1),
    LegacyPayload(1L shl 2),
    RequiresItemMapper(1L shl 3),
    InlineLoginBugReportFlag(1L shl 4),
    ExtendedSpriteFiles(1L shl 5),
    MagicEffectU16(1L shl 6),
    LoginSpeedFormula(1L shl 7),
    ModernLoginSideSystems(1L shl 8),
    ResourceBalancePackets(1L shl 9),
    CustomMonkPackets(1L shl 10),
    MarketPackets(1L shl 11),
    ImbuementWindow(1L shl 12),
    MemorialPackets(1L shl 13),
    PlayerDataLevelPercentU16(1L shl 14),
    GameEventPayload(1L shl 15),
    OfficialTaskboardPackets(1L shl 16),
    OfficialVocationSpecificPlayerData(1L shl 17),
    OfficialWeaponProficiencyPayload(1L shl 18),
    GraphicalEffectSourceByte(1L shl 19),
    OfficialSoulSealsPackets(1L shl 20),
    OfficialSkillWheelPayload(1L shl 21),
    TacticsWithoutFightMode(1L shl 22),
    ExpertPvpModeByte(1L shl 23),
}

fun protocolFeatureMask(feature: ProtocolFeature): Long = feature.mask

enum class TransportProfileId {
    RawClientFirst,
    CurrentModern,
    LegacyRawWithLoginHeader,
    LegacyClassic,
}

enum class OuterLengthEncoding {
    RawBodyLength,
    ModernBlockCount,
}

enum class EncryptedPayloadLayout {
    None,
    ModernPaddingByte,
    LegacyInnerLength,
}

enum class CompressionLayout {
    None,
    OpenTibia,
    Official,
}

data class TransportProfile(
    val id: TransportProfileId = TransportProfileId.RawClientFirst,
    val outerLength: OuterLengthEncoding = OuterLengthEncoding.RawBodyLength,
    val encryptedPayload: EncryptedPayloadLayout = EncryptedPayloadLayout.None,
    val inboundChecksum: ChecksumMethods = ChecksumMethods.None,
    val outboundChecksum: ChecksumMethods = ChecksumMethods.None,
    val compression: CompressionLayout = CompressionLayout.None,
    val modernLengthExtraBytes: Int = 0,
    val serverFirstPacketHeaderBytes: Int = 0,
    val hasCryptoHeader: Boolean = false,
    val lengthIncludesChecksum: Boolean = false,
    val sequenceHighBitSignalsCompression: Boolean = false,
)

enum class GameHandshakeFlow {
    ServerChallengeBeforeLogin,
    ClientFirstLoginPacket,
}

enum class ChallengeLayout {
    CurrentLoginChallenge,
    Tibia1100LoginChallenge,
    Cipsoft860LoginChallenge,
}

data class ChallengeProfile(
    val flow: GameHandshakeFlow = GameHandshakeFlow.ClientFirstLoginPacket,
    val layout: ChallengeLayout = ChallengeLayout.CurrentLoginChallenge,
)

data class InitialConnectionBehavior(
    val transport: TransportProfileId = TransportProfileId.RawClientFirst,
    val challenge: ChallengeProfile = ChallengeProfile(),
    val expectedProfile: ProtocolProfileId = ProtocolProfileId.Current,
) {
    fun hasSameWireBehavior(other: InitialConnectionBehavior): Boolean =
        transport == other.transport &&
            challenge.flow == other.challenge.flow &&
            challenge.layout == other.challenge.layout
}

data class ClientAssetSignatures(
    val dat: UInt = 0u,
    val spr: UInt = 0u,
    val pic: UInt = 0u,
)

data class ProtocolProfile(
    val id: ProtocolProfileId = ProtocolProfileId.Current,
    val clientVersion: Int = CLIENT_VERSION,
    val wireFamily: ClientWireFamily = ClientWireFamily.CipsoftVanilla,
    val rsaKeyFamily: RSAKeyFamily = RSAKeyFamily.OpenTibia,
    val supportState: ProtocolSupportState = ProtocolSupportState.Enabled,
    val itemMapperPolicy: ItemMapperPolicy = ItemMapperPolicy.NotRequired,
    val initialBehavior: InitialConnectionBehavior = InitialConnectionBehavior(),
    val assetSignatures: ClientAssetSignatures = ClientAssetSignatures(),
    val features: Long = ProtocolFeature.None.mask,
    val name: String = "",
    val supportLabel: String = "",
) {
    fun hasFeature(feature: ProtocolFeature): Boolean = (features and feature.mask) != 0L
}

enum class AccountCharacterListLayout {
    WorldListWithSessionKey,
    LegacyCharacterList,
}

enum class GameLoginAuthenticationLayout {
    SessionKey,
    AccountPassword,
}

data class AccountLoginLayout(
    val profileId: ProtocolProfileId = ProtocolProfileId.Current,
    val clientVersion: Int = CLIENT_VERSION,
    val responseTransport: TransportProfileId = TransportProfileId.CurrentModern,
    val bytesToSkipBeforeRsa: Int = 17,
    val hasAssetSignaturesBeforeRsa: Boolean = false,
    val characterListLayout: AccountCharacterListLayout = AccountCharacterListLayout.WorldListWithSessionKey,
    val sendsSessionKey: Boolean = true,
)

data class GameLoginLayout(
    val profileId: ProtocolProfileId = ProtocolProfileId.Current,
    val clientVersion: Int = CLIENT_VERSION,
    val hasClientVersionU32: Boolean = true,
    val hasClientVersionString: Boolean = true,
    val hasAssetHashString: Boolean = true,
    val hasContentRevisionU16: Boolean = false,
    val hasPreviewState: Boolean = true,
    val authenticationLayout: GameLoginAuthenticationLayout = GameLoginAuthenticationLayout.SessionKey,
    val hasChallengeResponse: Boolean = true,
    val hasOtcV8Probe: Boolean = true,
)

object ProtocolProfileRegistry {

    private val rawClientFirstTransport = TransportProfile(
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
    )

    private val currentModernTransport = TransportProfile(
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
    )

    private val legacyRawWithLoginHeaderTransport = TransportProfile(
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
    )

    private val legacyClassicTransport = TransportProfile(
        id = TransportProfileId.LegacyClassic,
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
    )

    private val currentInitialBehavior = InitialConnectionBehavior(
        transport = TransportProfileId.CurrentModern,
        challenge = ChallengeProfile(
            flow = GameHandshakeFlow.ServerChallengeBeforeLogin,
            layout = ChallengeLayout.CurrentLoginChallenge,
        ),
        expectedProfile = ProtocolProfileId.Current,
    )

    private val tibia1100InitialBehavior = InitialConnectionBehavior(
        transport = TransportProfileId.LegacyRawWithLoginHeader,
        challenge = ChallengeProfile(
            flow = GameHandshakeFlow.ServerChallengeBeforeLogin,
            layout = ChallengeLayout.Tibia1100LoginChallenge,
        ),
        expectedProfile = ProtocolProfileId.Tibia1100,
    )

    private val cipsoft860InitialBehavior = InitialConnectionBehavior(
        transport = TransportProfileId.LegacyClassic,
        challenge = ChallengeProfile(
            flow = GameHandshakeFlow.ServerChallengeBeforeLogin,
            layout = ChallengeLayout.Cipsoft860LoginChallenge,
        ),
        expectedProfile = ProtocolProfileId.Cipsoft860Vanilla,
    )

    private val cipsoft860ExtendedAssetsInitialBehavior = InitialConnectionBehavior(
        transport = TransportProfileId.LegacyClassic,
        challenge = ChallengeProfile(
            flow = GameHandshakeFlow.ServerChallengeBeforeLogin,
            layout = ChallengeLayout.Cipsoft860LoginChallenge,
        ),
        expectedProfile = ProtocolProfileId.Cipsoft860ExtendedAssets,
    )

    private val cipsoft860CanaryExtendedInitialBehavior = InitialConnectionBehavior(
        transport = TransportProfileId.LegacyClassic,
        challenge = ChallengeProfile(
            flow = GameHandshakeFlow.ServerChallengeBeforeLogin,
            layout = ChallengeLayout.Cipsoft860LoginChallenge,
        ),
        expectedProfile = ProtocolProfileId.Cipsoft860CanaryExtended,
    )

    private val otcv8Extended860InitialBehavior = InitialConnectionBehavior(
        transport = TransportProfileId.LegacyClassic,
        challenge = ChallengeProfile(
            flow = GameHandshakeFlow.ServerChallengeBeforeLogin,
            layout = ChallengeLayout.Cipsoft860LoginChallenge,
        ),
        expectedProfile = ProtocolProfileId.OTCv8Extended860,
    )

    private val cipsoft860CanaryAssetSignatures = ClientAssetSignatures(
        dat = 0x44363843u,
        spr = 0x53363843u,
        pic = 0x50363843u,
    )

    private val cipsoft860DevelopmentAssetSignatures = ClientAssetSignatures(
        dat = 0x4C2C7993u,
        spr = 0x4C220594u,
        pic = 0x4AE5C3D3u,
    )

    private val cipsoft860ExtendedClientLibrarySignatures = ClientAssetSignatures(
        dat = 0x44545845u,
        spr = 0x44545845u,
        pic = 0x44545845u,
    )

    private val cipsoft860ExtendedClientLibrarySpriteSignature = ClientAssetSignatures(
        dat = 0x4C2C7993u,
        spr = 0x44545845u,
        pic = 0x4AE5C3D3u,
    )

    private fun isCipsoft860CanaryAssetPackage(signatures: ClientAssetSignatures): Boolean =
        signatures == cipsoft860CanaryAssetSignatures ||
            signatures == cipsoft860DevelopmentAssetSignatures ||
            signatures == cipsoft860ExtendedClientLibrarySignatures ||
            signatures == cipsoft860ExtendedClientLibrarySpriteSignature

    private val currentProfile = ProtocolProfile(
        id = ProtocolProfileId.Current,
        clientVersion = CLIENT_VERSION,
        wireFamily = ClientWireFamily.CipsoftVanilla,
        rsaKeyFamily = RSAKeyFamily.OpenTibia,
        supportState = ProtocolSupportState.Enabled,
        itemMapperPolicy = ItemMapperPolicy.NotRequired,
        initialBehavior = currentInitialBehavior,
        features = protocolFeatureMask(ProtocolFeature.CurrentPayload) or
            protocolFeatureMask(ProtocolFeature.LoginSpeedFormula) or
            protocolFeatureMask(ProtocolFeature.ModernLoginSideSystems) or
            protocolFeatureMask(ProtocolFeature.ResourceBalancePackets) or
            protocolFeatureMask(ProtocolFeature.CustomMonkPackets) or
            protocolFeatureMask(ProtocolFeature.MarketPackets) or
            protocolFeatureMask(ProtocolFeature.ImbuementWindow) or
            protocolFeatureMask(ProtocolFeature.MemorialPackets) or
            protocolFeatureMask(ProtocolFeature.PlayerDataLevelPercentU16) or
            protocolFeatureMask(ProtocolFeature.GameEventPayload) or
            protocolFeatureMask(ProtocolFeature.OfficialTaskboardPackets) or
            protocolFeatureMask(ProtocolFeature.OfficialVocationSpecificPlayerData) or
            protocolFeatureMask(ProtocolFeature.OfficialWeaponProficiencyPayload) or
            protocolFeatureMask(ProtocolFeature.GraphicalEffectSourceByte) or
            protocolFeatureMask(ProtocolFeature.OfficialSoulSealsPackets) or
            protocolFeatureMask(ProtocolFeature.OfficialSkillWheelPayload) or
            protocolFeatureMask(ProtocolFeature.TacticsWithoutFightMode),
        name = "current",
        supportLabel = "",
    )

    private val tibia1100Profile = ProtocolProfile(
        id = ProtocolProfileId.Tibia1100,
        clientVersion = 1100,
        wireFamily = ClientWireFamily.CipsoftVanilla,
        rsaKeyFamily = RSAKeyFamily.OpenTibia,
        supportState = ProtocolSupportState.Enabled,
        itemMapperPolicy = ItemMapperPolicy.NotRequired,
        initialBehavior = tibia1100InitialBehavior,
        features = protocolFeatureMask(ProtocolFeature.OldProtocolCompat) or
            protocolFeatureMask(ProtocolFeature.LegacyPayload) or
            protocolFeatureMask(ProtocolFeature.LoginSpeedFormula) or
            protocolFeatureMask(ProtocolFeature.MarketPackets) or
            protocolFeatureMask(ProtocolFeature.ImbuementWindow) or
            protocolFeatureMask(ProtocolFeature.ExpertPvpModeByte),
        name = "tibia1100",
        supportLabel = "10x",
    )

    private val cipsoft860Profile = ProtocolProfile(
        id = ProtocolProfileId.Cipsoft860Vanilla,
        clientVersion = 860,
        wireFamily = ClientWireFamily.CipsoftVanilla,
        rsaKeyFamily = RSAKeyFamily.OpenTibia,
        supportState = ProtocolSupportState.Enabled,
        itemMapperPolicy = ItemMapperPolicy.RequiredBeforeWorldEnter,
        initialBehavior = cipsoft860InitialBehavior,
        features = protocolFeatureMask(ProtocolFeature.OldProtocolCompat) or
            protocolFeatureMask(ProtocolFeature.LegacyPayload) or
            protocolFeatureMask(ProtocolFeature.RequiresItemMapper) or
            protocolFeatureMask(ProtocolFeature.InlineLoginBugReportFlag),
        name = "cipsoft860vanilla",
        supportLabel = "8.6",
    )

    private val cipsoft860ExtendedAssetsProfile = ProtocolProfile(
        id = ProtocolProfileId.Cipsoft860ExtendedAssets,
        clientVersion = 860,
        wireFamily = ClientWireFamily.CipsoftVanilla,
        rsaKeyFamily = RSAKeyFamily.OpenTibia,
        supportState = ProtocolSupportState.Enabled,
        itemMapperPolicy = ItemMapperPolicy.NotRequired,
        initialBehavior = cipsoft860ExtendedAssetsInitialBehavior,
        assetSignatures = cipsoft860DevelopmentAssetSignatures,
        features = protocolFeatureMask(ProtocolFeature.OldProtocolCompat) or
            protocolFeatureMask(ProtocolFeature.LegacyPayload) or
            protocolFeatureMask(ProtocolFeature.ExtendedSpriteFiles) or
            protocolFeatureMask(ProtocolFeature.InlineLoginBugReportFlag),
        name = "cipsoft860extendedassets",
        supportLabel = "8.6",
    )

    private val cipsoft860CanaryExtendedProfile = ProtocolProfile(
        id = ProtocolProfileId.Cipsoft860CanaryExtended,
        clientVersion = 860,
        wireFamily = ClientWireFamily.CipsoftVanilla,
        rsaKeyFamily = RSAKeyFamily.OpenTibia,
        supportState = ProtocolSupportState.Enabled,
        itemMapperPolicy = ItemMapperPolicy.NotRequired,
        initialBehavior = cipsoft860CanaryExtendedInitialBehavior,
        assetSignatures = cipsoft860CanaryAssetSignatures,
        features = protocolFeatureMask(ProtocolFeature.OldProtocolCompat) or
            protocolFeatureMask(ProtocolFeature.LegacyPayload) or
            protocolFeatureMask(ProtocolFeature.ExtendedSpriteFiles) or
            protocolFeatureMask(ProtocolFeature.MagicEffectU16) or
            protocolFeatureMask(ProtocolFeature.InlineLoginBugReportFlag),
        name = "cipsoft860canaryextended",
        supportLabel = "8.6",
    )

    private val otcv8Extended860Profile = ProtocolProfile(
        id = ProtocolProfileId.OTCv8Extended860,
        clientVersion = 860,
        wireFamily = ClientWireFamily.OTCv8Extended,
        rsaKeyFamily = RSAKeyFamily.OpenTibia,
        supportState = ProtocolSupportState.BlockedPendingFixture,
        itemMapperPolicy = ItemMapperPolicy.RequiredBeforeWorldEnter,
        initialBehavior = otcv8Extended860InitialBehavior,
        features = protocolFeatureMask(ProtocolFeature.OldProtocolCompat) or
            protocolFeatureMask(ProtocolFeature.LegacyPayload) or
            protocolFeatureMask(ProtocolFeature.RequiresItemMapper) or
            protocolFeatureMask(ProtocolFeature.InlineLoginBugReportFlag),
        name = "otcv8extended860",
        supportLabel = "8.6",
    )

    private val registeredProfiles = listOf(
        currentProfile,
        tibia1100Profile,
        cipsoft860Profile,
        cipsoft860ExtendedAssetsProfile,
        cipsoft860CanaryExtendedProfile,
        otcv8Extended860Profile,
    )

    private val currentAccountLoginLayout = AccountLoginLayout(
        profileId = ProtocolProfileId.Current,
        clientVersion = CLIENT_VERSION,
        responseTransport = TransportProfileId.CurrentModern,
        bytesToSkipBeforeRsa = 17,
        characterListLayout = AccountCharacterListLayout.WorldListWithSessionKey,
        sendsSessionKey = true,
    )

    private val tibia1100AccountLoginLayout = AccountLoginLayout(
        profileId = ProtocolProfileId.Tibia1100,
        clientVersion = 1100,
        responseTransport = TransportProfileId.LegacyClassic,
        bytesToSkipBeforeRsa = 17,
        characterListLayout = AccountCharacterListLayout.WorldListWithSessionKey,
        sendsSessionKey = true,
    )

    private val cipsoft860AccountLoginLayout = AccountLoginLayout(
        profileId = ProtocolProfileId.Cipsoft860Vanilla,
        clientVersion = 860,
        responseTransport = TransportProfileId.LegacyClassic,
        bytesToSkipBeforeRsa = 12,
        hasAssetSignaturesBeforeRsa = true,
        characterListLayout = AccountCharacterListLayout.LegacyCharacterList,
        sendsSessionKey = false,
    )

    private val cipsoft860ExtendedAssetsAccountLoginLayout = AccountLoginLayout(
        profileId = ProtocolProfileId.Cipsoft860ExtendedAssets,
        clientVersion = 860,
        responseTransport = TransportProfileId.LegacyClassic,
        bytesToSkipBeforeRsa = 12,
        hasAssetSignaturesBeforeRsa = true,
        characterListLayout = AccountCharacterListLayout.LegacyCharacterList,
        sendsSessionKey = false,
    )

    private val cipsoft860CanaryExtendedAccountLoginLayout = AccountLoginLayout(
        profileId = ProtocolProfileId.Cipsoft860CanaryExtended,
        clientVersion = 860,
        responseTransport = TransportProfileId.LegacyClassic,
        bytesToSkipBeforeRsa = 12,
        hasAssetSignaturesBeforeRsa = true,
        characterListLayout = AccountCharacterListLayout.LegacyCharacterList,
        sendsSessionKey = false,
    )

    private val currentGameLoginLayout = GameLoginLayout(
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
    )

    private val tibia1100GameLoginLayout = GameLoginLayout(
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
    )

    private val cipsoft860GameLoginLayout = GameLoginLayout(
        profileId = ProtocolProfileId.Cipsoft860Vanilla,
        clientVersion = 860,
        hasClientVersionU32 = false,
        hasClientVersionString = false,
        hasAssetHashString = false,
        hasContentRevisionU16 = false,
        hasPreviewState = false,
        authenticationLayout = GameLoginAuthenticationLayout.AccountPassword,
        hasChallengeResponse = true,
        hasOtcV8Probe = false,
    )

    private val cipsoft860ExtendedAssetsGameLoginLayout = GameLoginLayout(
        profileId = ProtocolProfileId.Cipsoft860ExtendedAssets,
        clientVersion = 860,
        hasClientVersionU32 = false,
        hasClientVersionString = false,
        hasAssetHashString = false,
        hasContentRevisionU16 = false,
        hasPreviewState = false,
        authenticationLayout = GameLoginAuthenticationLayout.AccountPassword,
        hasChallengeResponse = true,
        hasOtcV8Probe = false,
    )

    private val cipsoft860CanaryExtendedGameLoginLayout = GameLoginLayout(
        profileId = ProtocolProfileId.Cipsoft860CanaryExtended,
        clientVersion = 860,
        hasClientVersionU32 = false,
        hasClientVersionString = false,
        hasAssetHashString = false,
        hasContentRevisionU16 = false,
        hasPreviewState = false,
        authenticationLayout = GameLoginAuthenticationLayout.AccountPassword,
        hasChallengeResponse = true,
        hasOtcV8Probe = false,
    )

    fun getTransportProfile(id: TransportProfileId): TransportProfile = when (id) {
        TransportProfileId.CurrentModern -> currentModernTransport
        TransportProfileId.LegacyRawWithLoginHeader -> legacyRawWithLoginHeaderTransport
        TransportProfileId.LegacyClassic -> legacyClassicTransport
        TransportProfileId.RawClientFirst -> rawClientFirstTransport
    }

    fun getCurrentProfile(): ProtocolProfile = currentProfile

    fun getProfile(id: ProtocolProfileId): ProtocolProfile? = when (id) {
        ProtocolProfileId.Current -> currentProfile
        ProtocolProfileId.Tibia1100 -> tibia1100Profile
        ProtocolProfileId.Cipsoft860Vanilla -> cipsoft860Profile
        ProtocolProfileId.Cipsoft860ExtendedAssets -> cipsoft860ExtendedAssetsProfile
        ProtocolProfileId.Cipsoft860CanaryExtended -> cipsoft860CanaryExtendedProfile
        ProtocolProfileId.OTCv8Extended860 -> otcv8Extended860Profile
    }

    fun resolveByClientVersion(
        version: Int,
        family: ClientWireFamily = ClientWireFamily.CipsoftVanilla,
    ): ProtocolProfile? {
        if (version == CLIENT_VERSION) {
            return currentProfile
        }
        if (version == 1100) {
            return tibia1100Profile
        }
        if (version == 860) {
            if (family == ClientWireFamily.OTCv8Extended) {
                return otcv8Extended860Profile
            }
            return cipsoft860Profile
        }
        return null
    }

    fun resolveByClientVersionAndAssets(
        version: Int,
        signatures: ClientAssetSignatures,
        family: ClientWireFamily = ClientWireFamily.CipsoftVanilla,
    ): ProtocolProfile? {
        val profile = resolveByClientVersion(version, family) ?: return null
        if (version == 860 && family == ClientWireFamily.CipsoftVanilla && isCipsoft860CanaryAssetPackage(signatures)) {
            return cipsoft860CanaryExtendedProfile
        }
        return profile
    }

    fun resolveAccountLoginLayout(version: Int): AccountLoginLayout? {
        if (version == CLIENT_VERSION) {
            return currentAccountLoginLayout
        }
        if (version == 1100) {
            return tibia1100AccountLoginLayout
        }
        if (version == 860) {
            return cipsoft860AccountLoginLayout
        }
        return null
    }

    fun resolveAccountLoginLayout(id: ProtocolProfileId): AccountLoginLayout? = when (id) {
        ProtocolProfileId.Current -> currentAccountLoginLayout
        ProtocolProfileId.Tibia1100 -> tibia1100AccountLoginLayout
        ProtocolProfileId.Cipsoft860Vanilla -> cipsoft860AccountLoginLayout
        ProtocolProfileId.Cipsoft860ExtendedAssets -> cipsoft860ExtendedAssetsAccountLoginLayout
        ProtocolProfileId.Cipsoft860CanaryExtended -> cipsoft860CanaryExtendedAccountLoginLayout
        ProtocolProfileId.OTCv8Extended860 -> null
    }

    fun resolveGameLoginLayout(version: Int): GameLoginLayout? {
        if (version == CLIENT_VERSION) {
            return currentGameLoginLayout
        }
        if (version == 1100) {
            return tibia1100GameLoginLayout
        }
        if (version == 860) {
            return cipsoft860GameLoginLayout
        }
        return null
    }

    fun resolveGameLoginLayout(id: ProtocolProfileId): GameLoginLayout? = when (id) {
        ProtocolProfileId.Current -> currentGameLoginLayout
        ProtocolProfileId.Tibia1100 -> tibia1100GameLoginLayout
        ProtocolProfileId.Cipsoft860Vanilla -> cipsoft860GameLoginLayout
        ProtocolProfileId.Cipsoft860ExtendedAssets -> cipsoft860ExtendedAssetsGameLoginLayout
        ProtocolProfileId.Cipsoft860CanaryExtended -> cipsoft860CanaryExtendedGameLoginLayout
        ProtocolProfileId.OTCv8Extended860 -> null
    }

    fun defaultModernInitialBehavior(): InitialConnectionBehavior = currentInitialBehavior

    fun getAllowedClientProtocolDescription(includeOldProtocolProfiles: Boolean): String {
        val labels = mutableListOf(currentClientVersionLabel())
        if (includeOldProtocolProfiles) {
            for (profile in registeredProfiles) {
                if (profile.id == ProtocolProfileId.Current ||
                    !isProfileAllowed(profile.id) ||
                    profile.supportLabel.isEmpty() ||
                    !profile.hasFeature(ProtocolFeature.OldProtocolCompat)
                ) {
                    continue
                }
                if (labels.none { it == profile.supportLabel }) {
                    labels.add(profile.supportLabel)
                }
            }
        }
        return joinProtocolLabels(labels)
    }

    fun getUnsupportedClientProtocolMessage(includeOldProtocolProfiles: Boolean): String {
        val description = getAllowedClientProtocolDescription(includeOldProtocolProfiles)
        val hasMultipleProtocols = description.contains(", ") || description.contains(" and ")
        return if (hasMultipleProtocols) {
            "Only supported client protocols are $description."
        } else {
            "Only supported client protocol is $description."
        }
    }

    fun isProfileAllowed(id: ProtocolProfileId): Boolean =
        getProfile(id)?.supportState == ProtocolSupportState.Enabled

    private fun currentClientVersionLabel(): String {
        val upper = CLIENT_VERSION / 100
        val lower = CLIENT_VERSION % 100
        return "$upper." + (if (lower < 10) "0" else "") + lower
    }

    private fun joinProtocolLabels(labels: List<String>): String {
        if (labels.isEmpty()) {
            return ""
        }
        if (labels.size == 1) {
            return labels.first()
        }
        val result = StringBuilder()
        for (index in labels.indices) {
            if (index > 0) {
                result.append(if (index + 1 == labels.size) " and " else ", ")
            }
            result.append(labels[index])
        }
        return result.toString()
    }
}
