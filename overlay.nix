final: prev: {
  mill = prev.mill.overrideAttrs (oldAttrs: rec {
    version = "0.11.1";
    src = prev.fetchurl {
      url = "https://github.com/com-lihaoyi/mill/releases/download/${version}/${version}-assembly";
      hash = "sha256-qG+Ddn0BHUZX1VX5hO84exgRz8YuUgYF/fH6MmgkrXE=";
    };
  });
  softfloat = final.callPackage ./nix/softfloat.nix { };
  testfloat = final.callPackage ./nix/testfloat.nix { };
}
