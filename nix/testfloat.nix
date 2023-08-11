{ stdenv, fetchFromGitHub, softfloat }:
stdenv.mkDerivation rec {
  pname = "softfloat";
  version = "06b20075dd3c1a5d0dd007a93643282832221612";
  src = fetchFromGitHub {
    owner = "ucb-bar";
    repo = "berkeley-testfloat-3";
    rev = version;
    sha256 = "sha256-4C0a3jmmQPYlgbQ9F1frjtVixk3+wvLZFiujOhHshmw=";
  };
  buildPhase = ''
    make -C build/Linux-x86_64-GCC SPECIALIZE_TYPE=RISCV SOFTFLOAT_INCLUDE_DIR=${softfloat}/include SOFTFLOAT_LIB=${softfloat}/lib/softfloat.a
  '';
  installPhase = ''
    mkdir -p $out/bin
    cp build/Linux-x86_64-GCC/testfloat_gen $out/bin/testfloat_gen
  '';
}
