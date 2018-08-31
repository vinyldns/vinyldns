package vinyldns.api.crypto

import cats.effect.IO
import com.typesafe.config.Config
import vinyldns.api.VinylDNSConfig
import vinyldns.core.crypto.CryptoAlgebra

object Crypto {

  lazy val instance: CryptoAlgebra = loadCrypto().unsafeRunSync()

  def loadCrypto(cryptoConfig: Config = VinylDNSConfig.cryptoConfig): IO[CryptoAlgebra] =
    CryptoAlgebra.load(cryptoConfig)

  def encrypt(value: String): String = instance.encrypt(value)

  def decrypt(value: String): String = instance.decrypt(value)
}
