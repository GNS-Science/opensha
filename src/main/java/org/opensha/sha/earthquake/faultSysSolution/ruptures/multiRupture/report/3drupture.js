
function vertex([longitude, latitude], radius) {
  const lambda = longitude * Math.PI / 180;
  const phi = latitude * Math.PI / 180;
  return new THREE.Vector3(
    radius * Math.cos(phi) * Math.cos(lambda),
    radius * Math.sin(phi),
    -radius * Math.cos(phi) * Math.sin(lambda)
  );
}

camera = {
  const fov = 70;
  const aspect = width / height;
  const near = 1;
  const far = 1000;
  return new THREE.PerspectiveCamera(fov, aspect, near, far);
}

       const scene = new THREE.Scene();
//       const camera = new THREE.PerspectiveCamera( 75, window.innerWidth / window.innerHeight, 0.1, 1000 );

       const renderer = new THREE.WebGLRenderer();
       renderer.setSize( window.innerWidth, window.innerHeight );
        document.body.appendChild( renderer.domElement );

        const geometry = new THREE.BoxGeometry( 1, 1, 1 );
const material = new THREE.MeshBasicMaterial( { color: 0x00ff00 } );
const cube = new THREE.Mesh( geometry, material );
scene.add( cube );

camera.position.z = 5;

        function animate() {
        cube.rotation.x += 0.01;
cube.rotation.y += 0.01;
	renderer.render( scene, camera );
}
renderer.setAnimationLoop( animate );


        // var geojson = require('./joint-rupture-0.geojson');