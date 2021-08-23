{
  description = "Content management system for open local knowledge";

  # Nixpkgs / NixOS version to use.
  inputs.nixpkgs.url = "nixpkgs/nixos-21.05";

  inputs.clj2nix_src = {
    url = "github:hlolli/clj2nix";
    flake = false;
  };

  inputs.npmlock2nix_src = {
    #url = "github:tweag/npmlock2nix";
    url = "/home/sohalt/projects/summer-of-nix/npmlock2nix";
    flake = false;
  };

  outputs = { self, nixpkgs, npmlock2nix_src, clj2nix_src }:
    let

      # Generate a user-friendly version numer.
      version = builtins.substring 0 8 self.lastModifiedDate;

      # System types to support.
      supportedSystems = [ "x86_64-linux" "x86_64-darwin" ];

      # Helper function to generate an attrset '{ x86_64-linux = f "x86_64-linux"; ... }'.
      forAllSystems = f: nixpkgs.lib.genAttrs supportedSystems (system: f system);

      # Nixpkgs instantiated for supported system types.
      nixpkgsFor = forAllSystems (system: import nixpkgs { inherit system; overlays = [ self.overlay ]; });

    in

    {

      # A Nixpkgs overlay.
      overlay = final: prev: {

        geopub = with final;
	let
	npmlock2nix = final.callPackage npmlock2nix_src {};
	cljdeps = final.callPackage ./deps.nix {};
	classp = cljdeps.makeClasspaths { extraClasspaths = "src"; };
	in
	npmlock2nix.build {
          name = "geopub-${version}";

          src = ./.;

	  HOME="/tmp";

	  nativeBuildInputs = [ clojure ];

          buildPhase = ''
            clj -Scp ${classp} -m shadow.cljs.devtools.cli release app
          '';

	  installPhase = ''
            mkdir -p $out/js
            cp -r resources/public/* $out/
            cp target/js/main.js $out/js/main.js
	  '';
	  };
      };

      # Provide some binary packages for selected system types.
      packages = forAllSystems (system:
        {
          inherit (nixpkgsFor.${system}) geopub;
        });

      # The default package for 'nix build'. This makes sense if the
      # flake provides only one package or there is a clear "main"
      # package.
      defaultPackage = forAllSystems (system: self.packages.${system}.geopub);

      # A NixOS module, if applicable (e.g. if the package provides a system service).
      nixosModules.geopub =
        { pkgs, ... }:
        {
          nixpkgs.overlays = [ self.overlay ];

          environment.systemPackages = [ pkgs.geopub ];

          #systemd.services = { ... };
        };

      # Tests run by 'nix flake check' and by Hydra.
      checks = forAllSystems
        (system:
          with nixpkgsFor.${system};

          {
            inherit (self.packages.${system}) geopub;

            # Additional tests, if applicable.
            test = stdenv.mkDerivation {
              name = "geopub-test-${version}";

              buildInputs = [ geopub ];

              unpackPhase = "true";

              buildPhase = ''
                echo 'running some integration tests'
                [[ $(geopub) = 'Geopub Nixers!' ]]
              '';

              installPhase = "mkdir -p $out";
            };
          }

          // lib.optionalAttrs stdenv.isLinux {
            # A VM test of the NixOS module.
            vmTest =
              with import (nixpkgs + "/nixos/lib/testing-python.nix") {
                inherit system;
              };

              makeTest {
                nodes = {
                  client = { ... }: {
                    imports = [ self.nixosModules.geopub ];
                  };
                };

                testScript =
                  ''
                    start_all()
                    client.wait_for_unit("multi-user.target")
                    client.succeed("geopub")
                  '';
              };
          }
        );

    devShell = forAllSystems (system:
	let 
	  clj2nix = nixpkgsFor.${system}.callPackage clj2nix_src {};
	in
	nixpkgsFor.${system}.mkShell {
	  buildInputs = [ clj2nix ];
	}
	);
    };
}
